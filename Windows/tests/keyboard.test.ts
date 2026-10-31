import test from 'node:test';
import assert from 'node:assert/strict';
import {
    adjacentTermIndex, createShortcutHandler, glossaryDirection, isQuizAdvanceKey, quizOptionIndex,
    type ShortcutContext,
} from '../src/renderer/keyboard';

class KeyEvent extends Event {
    readonly key: string;
    readonly code: string;
    readonly repeat: boolean;
    readonly ctrlKey: boolean;
    readonly metaKey: boolean;
    readonly altKey: boolean;
    readonly shiftKey: boolean;
    readonly isComposing: boolean;
    readonly keyCode: number;

    constructor(key: string, options: Partial<KeyboardEventInit> & { keyCode?: number } = {}) {
        super('keydown', { cancelable: true });
        this.key = key;
        this.code = options.code ?? '';
        this.repeat = options.repeat ?? false;
        this.ctrlKey = options.ctrlKey ?? false;
        this.metaKey = options.metaKey ?? false;
        this.altKey = options.altKey ?? false;
        this.shiftKey = options.shiftKey ?? false;
        this.isComposing = options.isComposing ?? false;
        this.keyCode = options.keyCode ?? 0;
    }
}

function harness(perform: (key: string) => boolean, recognizes: (key: string) => boolean) {
    const context: ShortcutContext = {
        focused: true, visible: true, blockedTarget: false,
        overlayOpen: false, hasSelection: false, composing: false,
    };
    const target = new EventTarget();
    const handle = createShortcutHandler(() => context, perform, recognizes);
    const listener = (event: Event) => handle(event as KeyEvent);
    target.addEventListener('keydown', listener);
    return {
        context,
        send(key: string, options: Partial<KeyboardEventInit> & { keyCode?: number } = {}, prevented = false) {
            const event = new KeyEvent(key, options);
            if (prevented) event.preventDefault();
            target.dispatchEvent(event);
            return event;
        },
        dispose: () => target.removeEventListener('keydown', listener),
    };
}

test('all four glossary arrows use the current filtered order without wrapping', () => {
    const ids = [3, 18, 104];
    let selected: number | null = null;
    const hub = harness(key => {
        const direction = glossaryDirection(key);
        if (direction === null) return false;
        const index = adjacentTermIndex(ids.length, ids.indexOf(selected ?? -1), direction);
        if (index === null) return false;
        selected = ids[index];
        return true;
    }, key => glossaryDirection(key) !== null);
    assert.equal(hub.send('ArrowRight').defaultPrevented, true);
    assert.equal(selected, 3);
    hub.send('ArrowDown'); assert.equal(selected, 18);
    hub.send('ArrowRight'); assert.equal(selected, 104);
    hub.send('ArrowDown'); assert.equal(selected, 104);
    hub.send('ArrowLeft'); assert.equal(selected, 18);
    hub.send('ArrowUp'); assert.equal(selected, 3);
    assert.equal(hub.send('ArrowLeft').defaultPrevented, true);
    assert.equal(selected, 3);
    selected = null;
    hub.send('ArrowUp'); assert.equal(selected, 104);
    selected = null;
    hub.send('ArrowLeft'); assert.equal(selected, 104);
    hub.dispose();
});

test('empty and one-term lists are safe and no-selection starts at the requested end', () => {
    assert.equal(adjacentTermIndex(0, -1, 1), null);
    assert.equal(adjacentTermIndex(0, -1, -1), null);
    assert.equal(adjacentTermIndex(1, -1, -1), 0);
    assert.equal(adjacentTermIndex(1, 0, 1), 0);
    assert.equal(adjacentTermIndex(1, 0, -1), 0);
    assert.equal(adjacentTermIndex(326, -1, -1), 325);
    assert.equal(adjacentTermIndex(326, -1, 1), 0);
    assert.equal(glossaryDirection('Enter'), null);
});

test('keydown dispatch consumes held recognized keys without running their action', () => {
    let calls = 0;
    const hub = harness(() => { calls += 1; return true; }, key => key === 'ArrowRight');
    hub.send('ArrowRight');
    assert.equal(hub.send('ArrowRight', { repeat: true }).defaultPrevented, true);
    assert.equal(calls, 1);
    assert.equal(hub.send('x', { repeat: true }).defaultPrevented, false);
    hub.dispose();
    hub.send('ArrowRight');
    assert.equal(calls, 1);
});

test('keydown respects focus, visibility, editing, overlays, selection and composition contexts', () => {
    let calls = 0;
    const hub = harness(() => { calls += 1; return true; }, () => true);
    const blockers: Partial<ShortcutContext>[] = [
        { focused: false }, { visible: false }, { blockedTarget: true },
        { overlayOpen: true }, { hasSelection: true }, { composing: true },
    ];
    for (const blocker of blockers) {
        const previous = { ...hub.context };
        Object.assign(hub.context, blocker);
        assert.equal(hub.send('ArrowDown').defaultPrevented, false, JSON.stringify(blocker));
        assert.equal(hub.send('ArrowDown', { repeat: true }).defaultPrevented, false);
        Object.assign(hub.context, previous);
    }
    assert.equal(calls, 0);
    assert.equal(hub.send('ArrowDown').defaultPrevented, true);
    assert.equal(calls, 1);
});

test('IME, native-consumed events and modified navigation are never intercepted', () => {
    let calls = 0;
    const hub = harness(() => { calls += 1; return true; }, () => true);
    for (const options of [
        { isComposing: true }, { keyCode: 229 }, { ctrlKey: true }, { metaKey: true },
        { altKey: true }, { shiftKey: true },
    ]) assert.equal(hub.send('ArrowRight', options).defaultPrevented, false);
    hub.send('ArrowRight', {}, true);
    assert.equal(calls, 0);
});

test('letters, uppercase and numpad digit key events select the same displayed positions', () => {
    const selected: number[] = [];
    const hub = harness(key => {
        const index = quizOptionIndex(key);
        if (index === null) return false;
        selected.push(index);
        return true;
    }, key => quizOptionIndex(key) !== null);
    for (const [index, letter] of ['a', 'b', 'c', 'd'].entries()) {
        hub.send(letter);
        hub.send(letter.toUpperCase());
        hub.send(letter.toUpperCase(), { shiftKey: true });
        hub.send(String(index + 1), { code: `Digit${index + 1}` });
        hub.send(String(index + 1), { code: `Numpad${index + 1}` });
    }
    assert.deepEqual(selected, [0, 1, 2, 3].flatMap(index => Array(5).fill(index)));
    assert.equal(hub.send('1', { shiftKey: true }).defaultPrevented, false);
    for (const key of ['0', '5', 'e', 'End', 'Process']) assert.equal(quizOptionIndex(key), null);
});

test('Enter, right and down are forward-only quiz continuation keys', () => {
    assert.ok(['Enter', 'ArrowRight', 'ArrowDown'].every(isQuizAdvanceKey));
    assert.ok(['ArrowLeft', 'ArrowUp', ' ', '1'].every(key => !isQuizAdvanceKey(key)));
});
