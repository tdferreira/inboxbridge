const browserApi = globalThis.browser ?? globalThis.chrome

function contextMenuApi() {
  return browserApi.contextMenus ?? browserApi.menus
}

function wrapChromeCall(fn, operation = 'browser API call') {
  return new Promise((resolve, reject) => {
    try {
      fn((result) => {
        const lastError = browserApi?.runtime?.lastError
        if (lastError) {
          reject(new Error(`${operation} failed: ${lastError.message}`))
          return
        }
        resolve(result)
      })
    } catch (error) {
      reject(error)
    }
  })
}

export async function storageGet(keys) {
  return wrapChromeCall((done) => browserApi.storage.local.get(keys, done), 'storage.local.get')
}

export async function storageSet(values) {
  await wrapChromeCall((done) => browserApi.storage.local.set(values, done), 'storage.local.set')
}

export async function storageRemove(keys) {
  await wrapChromeCall((done) => browserApi.storage.local.remove(keys, done), 'storage.local.remove')
}

export async function requestOriginPermission(originPattern) {
  return wrapChromeCall((done) => browserApi.permissions.request({ origins: [originPattern] }, done), 'permissions.request')
}

export async function containsOriginPermission(originPattern) {
  return wrapChromeCall((done) => browserApi.permissions.contains({ origins: [originPattern] }, done), 'permissions.contains')
}

export async function requestApiPermission(permission) {
  return wrapChromeCall((done) => browserApi.permissions.request({ permissions: [permission] }, done), 'permissions.request')
}

export async function containsApiPermission(permission) {
  return wrapChromeCall((done) => browserApi.permissions.contains({ permissions: [permission] }, done), 'permissions.contains')
}

export async function setBadge(text, color, title) {
  await wrapChromeCall((done) => browserApi.action.setBadgeText({ text }, done), 'action.setBadgeText')
  if (color) {
    await wrapChromeCall((done) => browserApi.action.setBadgeBackgroundColor({ color }, done), 'action.setBadgeBackgroundColor')
  }
  if (title) {
    await wrapChromeCall((done) => browserApi.action.setTitle({ title }, done), 'action.setTitle')
  }
}

export async function setIcon(imageData) {
  await wrapChromeCall((done) => browserApi.action.setIcon({ imageData }, done), 'action.setIcon')
}

export async function createNotification(notificationId, options) {
  return wrapChromeCall((done) => browserApi.notifications.create(notificationId, options, done), 'notifications.create')
}

export async function removeAllContextMenus() {
  const menus = contextMenuApi()
  if (!menus?.removeAll) {
    return
  }
  await wrapChromeCall((done) => menus.removeAll(done), 'contextMenus.removeAll')
}

export async function createContextMenu(options) {
  const menus = contextMenuApi()
  if (!menus?.create) {
    return
  }
  await wrapChromeCall((done) => menus.create(options, done), 'contextMenus.create')
}

export async function clearBadge(title = 'InboxBridge') {
  await wrapChromeCall((done) => browserApi.action.setBadgeText({ text: '' }, done), 'action.setBadgeText')
  await wrapChromeCall((done) => browserApi.action.setTitle({ title }, done), 'action.setTitle')
}

export async function createAlarm(name, delayInMinutes, periodInMinutes) {
  browserApi.alarms.create(name, { delayInMinutes, periodInMinutes })
}

export function onAlarm(listener) {
  browserApi.alarms.onAlarm.addListener(listener)
}

export function onInstalled(listener) {
  browserApi.runtime.onInstalled.addListener(listener)
}

export function onContextMenuClicked(listener) {
  const menus = contextMenuApi()
  menus?.onClicked?.addListener(listener)
}

export function onMessage(listener) {
  browserApi.runtime.onMessage.addListener(listener)
}

export async function sendMessage(message) {
  return wrapChromeCall((done) => browserApi.runtime.sendMessage(message, done), 'runtime.sendMessage')
}

export async function openTab(url) {
  return wrapChromeCall((done) => browserApi.tabs.create({ url }, done), 'tabs.create')
}

export async function openOptionsPage() {
  return wrapChromeCall((done) => browserApi.runtime.openOptionsPage(done), 'runtime.openOptionsPage')
}

export async function queryTabs(queryInfo) {
  return wrapChromeCall((done) => browserApi.tabs.query(queryInfo, done), 'tabs.query')
}

export function browserRuntime() {
  return browserApi.runtime
}
