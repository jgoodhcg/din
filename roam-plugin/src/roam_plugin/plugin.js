let plugin = document.getElementById("din-roam-sync-main");
if (!plugin) {
  let plugin = document.createElement("script");
  plugin.src = "http://127.0.0.1:8000/assets/app/js/main.js";
  plugin.id = "din-roam-sync-main";
  plugin.async = true;
  plugin.type = "text/javascript";
  document.getElementsByTagName("head")[0].appendChild(plugin);
}
