let re = /\.(js|jsx|ts|tsx)$/

let req = require.context('./')

Object.defineProperty(exports, '__esModule', { value: true });

for (let key of req.keys()) {
  if (key === './index' || re.test(key)) continue;
  exports[key.slice(2)] = req(key).default;
}
