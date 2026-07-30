// GREX CHILD SHELL — TEMPLATE BUNDLE PLACEHOLDER
// This file is replaced at APK factory time by HomeScreenPlugin.installChildApk().
// The real file will contain the full Datacore component bundle (e.g. SocialBotEngine).
console.warn('[GrexChildShell] Placeholder bundle loaded. Template APK not yet mutated with real component.');
export function mount_app(container) {
  if (container) {
    container.innerHTML = '<div style="color:#a855f7;padding:32px;font-family:monospace;font-size:13px;">Grex Child Shell: Template APK placeholder. Export a component from Grex Nexus Mobile to populate this app.</div>';
  }
}
export default mount_app;
