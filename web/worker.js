// Runs the Python converter (web/convert.py + the caserecomp package) in Pyodide,
// off the page's main thread. Game files stay in this tab's memory.
importScripts("https://cdn.jsdelivr.net/pyodide/v0.26.4/full/pyodide.js");

let pyodide = null;

async function ready() {
  if (pyodide) return pyodide;
  postMessage({ type: "status", key: "loadingPython" });
  pyodide = await loadPyodide();
  await pyodide.loadPackage(["Pillow"]);
  const [pkg, convert] = await Promise.all([
    fetch("caserecomp.zip").then((r) => { if (!r.ok) throw new Error("caserecomp.zip " + r.status); return r.arrayBuffer(); }),
    fetch("convert.py").then((r) => { if (!r.ok) throw new Error("convert.py " + r.status); return r.text(); }),
  ]);
  pyodide.FS.mkdirTree("/lib");
  pyodide.unpackArchive(pkg, "zip", { extractDir: "/lib" });
  pyodide.FS.writeFile("/lib/convert.py", convert);
  pyodide.runPython("import sys; sys.path.insert(0, '/lib'); import convert");
  return pyodide;
}

function resetGame(py) {
  for (const dir of ["/game", "/out"]) {
    if (py.FS.analyzePath(dir).exists) py.runPython(`import shutil; shutil.rmtree(${JSON.stringify(dir)})`);
    py.FS.mkdirTree(dir);
  }
}

onmessage = async (event) => {
  const { type } = event.data;
  try {
    const py = await ready();
    if (type === "scan") {
      resetGame(py);
      for (const { path, data } of event.data.files) {
        const target = "/game/" + path.split("/").filter((p) => p && p !== "." && p !== "..").join("/");
        py.FS.mkdirTree(target.slice(0, target.lastIndexOf("/")) || "/game");
        py.FS.writeFile(target, new Uint8Array(data));
      }
      postMessage({ type: "scanned", report: JSON.parse(py.runPython("convert.scan('/game')")) });
    } else if (type === "convert") {
      postMessage({ type: "status", key: "converting" });
      const summary = JSON.parse(py.runPython("convert.convert('/game', '/out/game.director.zip')"));
      const bytes = py.FS.readFile("/out/game.director.zip");
      resetGame(py);
      postMessage({ type: "converted", summary, bytes }, [bytes.buffer]);
    }
  } catch (error) {
    postMessage({ type: "error", message: String(error && error.message ? error.message : error) });
  }
};
