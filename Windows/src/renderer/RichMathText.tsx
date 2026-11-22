import { useEffect } from 'react';
import manifest from '../../../Resources/Math/manifest.json';
import { mathSegments } from '../shared/math-source.mjs';

let readers = 0;

function normalizedRange(range: Range): Range {
    const result = range.cloneRange();
    const mathParent = (node: Node) => (node instanceof Element ? node : node.parentElement)?.closest('[data-math-source]');
    const start = mathParent(result.startContainer), end = mathParent(result.endContainer);
    if (start) result.setStartBefore(start);
    if (end) result.setEndAfter(end);
    return result;
}
export function mathSelectionText(selection: Selection | null): string {
    if (!selection || selection.isCollapsed || selection.rangeCount !== 1) return '';
    const fragment = normalizedRange(selection.getRangeAt(0)).cloneContents();
    for (const math of fragment.querySelectorAll('[data-math-source]')) math.replaceWith(document.createTextNode(math.getAttribute('data-math-source') ?? ''));
    return fragment.textContent ?? '';
}
function copyMath(event: ClipboardEvent) {
    if (event.defaultPrevented) return;
    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || !selection.rangeCount) return;
    if (!normalizedRange(selection.getRangeAt(0)).cloneContents().querySelector('[data-math-source]')) return;
    event.clipboardData?.setData('text/plain', mathSelectionText(selection)); event.preventDefault();
}

export function RichMathText({ source, selectable = true }: { source: string; selectable?: boolean }) {
    useEffect(() => {
        if (readers++ === 0) document.addEventListener('copy', copyMath);
        return () => { if (--readers === 0) document.removeEventListener('copy', copyMath); };
    }, []);
    return <>{mathSegments(source).map((part, index) => {
        if (part.kind === 'text') return part.text;
        const asset = manifest.entries.find(entry => entry.tex === part.text && entry.display === part.display);
        const svg = asset?.svgMarkup;
        if (!svg) return <span key={index} role="alert" className="math-error" data-math-source={part.source}>[公式显示异常：{part.source}]</span>;
        return <span key={index} className={`math-unit${part.display ? ' math-display' : ''}`} data-math-source={part.source}
            role="math" aria-label={part.source} title={`公式：${part.text}；复制时保留 LaTeX`}
            style={{ verticalAlign: `${-asset.depthEm}em` }}
            onClick={selectable ? event => {
                const range = document.createRange(); range.selectNode(event.currentTarget);
                const selection = window.getSelection(); selection?.removeAllRanges(); selection?.addRange(range);
            } : undefined}
            dangerouslySetInnerHTML={{ __html: svg }} />;
    })}</>;
}
