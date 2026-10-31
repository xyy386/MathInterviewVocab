import { useEffect, useRef } from 'react';

type KeyboardInput = Pick<KeyboardEvent,
    'key' | 'keyCode' | 'target' | 'defaultPrevented' | 'repeat' | 'isComposing'
    | 'ctrlKey' | 'metaKey' | 'altKey' | 'shiftKey' | 'preventDefault'>;

export interface ShortcutContext {
    focused: boolean;
    visible: boolean;
    blockedTarget: boolean;
    overlayOpen: boolean;
    hasSelection: boolean;
    composing: boolean;
}

export function createShortcutHandler(
    context: (event: KeyboardInput) => ShortcutContext,
    perform: (key: string) => boolean,
    recognizes: (key: string) => boolean,
): (event: KeyboardInput) => void {
    return event => {
        if (event.defaultPrevented || event.isComposing || event.keyCode === 229
            || event.ctrlKey || event.metaKey || event.altKey
            || (event.shiftKey && !/^[a-d]$/i.test(event.key))) return;
        const state = context(event);
        if (!state.focused || !state.visible || state.blockedTarget || state.overlayOpen
            || state.hasSelection || state.composing) return;
        if (event.repeat) {
            if (recognizes(event.key)) event.preventDefault();
            return;
        }
        if (perform(event.key)) event.preventDefault();
    };
}

function blockedElement(target: EventTarget | null): boolean {
    return target instanceof Element && (target instanceof HTMLElement && target.isContentEditable
        || target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"]), '
            + '[role="separator"], [role="combobox"], [role="menu"], [role="menuitem"], summary') !== null);
}

function browserContext(event: KeyboardInput, composing: boolean): ShortcutContext {
    const selection = window.getSelection();
    return {
        focused: document.hasFocus(),
        visible: document.visibilityState === 'visible',
        blockedTarget: blockedElement(event.target) || blockedElement(document.activeElement),
        overlayOpen: document.querySelector('[role="dialog"], [aria-modal="true"], dialog[open], '
            + 'details[open], [popover]:popover-open') !== null,
        hasSelection: Boolean(selection && !selection.isCollapsed && selection.toString().length > 0),
        composing,
    };
}

export function useKeyboardShortcuts(perform: (key: string) => boolean, recognizes: (key: string) => boolean): void {
    const action = useRef(perform);
    const recognized = useRef(recognizes);
    const composing = useRef(false);
    action.current = perform;
    recognized.current = recognizes;

    useEffect(() => {
        const onKeyDown = createShortcutHandler(
            event => browserContext(event, composing.current), key => action.current(key),
            key => recognized.current(key),
        );
        const beginComposition = () => { composing.current = true; };
        const endComposition = () => { composing.current = false; };
        window.addEventListener('keydown', onKeyDown);
        window.addEventListener('compositionstart', beginComposition);
        window.addEventListener('compositionend', endComposition);
        window.addEventListener('blur', endComposition);
        return () => {
            window.removeEventListener('keydown', onKeyDown);
            window.removeEventListener('compositionstart', beginComposition);
            window.removeEventListener('compositionend', endComposition);
            window.removeEventListener('blur', endComposition);
        };
    }, []);
}

export function glossaryDirection(key: string): -1 | 1 | null {
    if (key === 'ArrowLeft' || key === 'ArrowUp') return -1;
    if (key === 'ArrowRight' || key === 'ArrowDown') return 1;
    return null;
}

export function adjacentTermIndex(count: number, currentIndex: number, direction: -1 | 1): number | null {
    if (count === 0) return null;
    if (currentIndex < 0) return direction === 1 ? 0 : count - 1;
    return Math.max(0, Math.min(count - 1, currentIndex + direction));
}

export function quizOptionIndex(key: string): number | null {
    if (/^[1-4]$/.test(key)) return Number(key) - 1;
    if (/^[a-d]$/i.test(key)) return key.toLowerCase().charCodeAt(0) - 97;
    return null;
}

export function isQuizAdvanceKey(key: string): boolean {
    return key === 'Enter' || key === 'ArrowRight' || key === 'ArrowDown';
}
