import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('vocab', {
    load: () => ipcRenderer.invoke('vocab:load'),
    toggleFavorite: (id: number) => ipcRenderer.invoke('vocab:favorite', id),
    updateQuizPreferences: (preferences: unknown) => ipcRenderer.invoke('vocab:preferences', preferences),
    recordAnswer: (attempt: unknown) => ipcRenderer.invoke('vocab:answer', attempt),
    resetProgress: () => ipcRenderer.invoke('vocab:reset'),
    saveSettings: (settings: unknown) => ipcRenderer.invoke('vocab:settings', settings),
    getAudio: (text: string, accent: string) => ipcRenderer.invoke('vocab:audio', text, accent),
    showSelectionMenu: (text: string) => ipcRenderer.invoke('vocab:selection-menu', text),
    openSettings: () => ipcRenderer.invoke('vocab:open-settings'),
    confirmReset: () => ipcRenderer.invoke('vocab:confirm-reset'),
    windowAction: (action: string) => ipcRenderer.invoke('vocab:window-action', action),
    onStopAudio: (callback: () => void) => {
        const listener = () => callback();
        ipcRenderer.on('vocab:stop-audio', listener);
        return () => ipcRenderer.removeListener('vocab:stop-audio', listener);
    },
    onDataChanged: (callback: (change: unknown) => void) => {
        const listener = (_event: Electron.IpcRendererEvent, change: unknown) => callback(change);
        ipcRenderer.on('vocab:data-changed', listener);
        return () => ipcRenderer.removeListener('vocab:data-changed', listener);
    },
    onSelectionSpeak: (callback: (text: string) => void) => {
        const listener = (_event: Electron.IpcRendererEvent, text: string) => callback(text);
        ipcRenderer.on('vocab:speak-selection', listener);
        return () => ipcRenderer.removeListener('vocab:speak-selection', listener);
    },
});
