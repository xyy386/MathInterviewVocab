// 只识别词库明确标记的数学区域，不把正文中的下划线猜成公式。
export function mathSegments(source) {
    const segments = [];
    const pattern = /\\\(([\s\S]*?)\\\)|\\\[([\s\S]*?)\\\]/g;
    let end = 0;
    for (const match of source.matchAll(pattern)) {
        if (match.index > end) segments.push({ kind: 'text', text: source.slice(end, match.index).replaceAll('`', '') });
        segments.push({ kind: 'math', text: match[1] ?? match[2], source: match[0], display: match[2] !== undefined });
        end = match.index + match[0].length;
    }
    if (end < source.length) segments.push({ kind: 'text', text: source.slice(end).replaceAll('`', '') });
    return segments;
}

export function mathSearchText(source) {
    return source.normalize('NFKC').replace(/\\[()[\]{}]/g, '').replace(/[_^{}]/g, '').toLocaleLowerCase();
}
