import * as ExcalidrawLib from 'excalidraw';
import React from "react";
import { createRoot } from "react-dom-client";

// reference: https://github.com/TriliumNext/Notes/blob/develop/src/public/app/widgets/type_widgets/canvas.ts

window.ExcalidrawLib = ExcalidrawLib;

var noteId = window.location.href.substring("https://trilium-notes.invalid/".length);

var excalidraw = null;
var savedSceneNumber = -1;
var savedAppState = {};
var saveTimeoutID = -1;

function checkAndMaybeSave() {
    const elements = excalidraw.getSceneElements();
    const newSceneNumber = ExcalidrawLib.getSceneVersion(elements);
    const exAppState = excalidraw.getAppState();
    const newAppState = {
        scrollX: exAppState.scrollX,
        scrollY: exAppState.scrollY,
        zoom: exAppState.zoom
    };
    const appStateChanged = newAppState.scrollX !== savedAppState["scrollX"] || newAppState.scrollY !== savedAppState["scrollY"] || newAppState.zoom !== savedAppState["zoom"];
    if (newSceneNumber === savedSceneNumber && !appStateChanged) {
        return;
    }
    savedSceneNumber = newSceneNumber;
    savedAppState = newAppState;
    const files = excalidraw.getFiles();
    const activeFiles = {};
    elements.forEach((element) => {
        if ("fileId" in element && element.fileId) {
            activeFiles[element.fileId] = files[element.fileId];
        }
    });
    const content = {
        type: "excalidraw",
        version: 2,
        elements,
        files: activeFiles,
        appState: newAppState
    };
    // TODO: SVG export
    // TODO: Library export
    const contentJson = JSON.stringify(content);
    api.updateExcalidrawNote(noteId, contentJson);
}

const App = () => {
  return React.createElement(
    React.Fragment,
    null,
    React.createElement(
      "div",
      {
        style: { height: "100vh", width: "100vw" },
      },
      React.createElement(ExcalidrawLib.Excalidraw, {
        theme: window.getComputedStyle(document.documentElement).getPropertyValue("--theme-style"),
        excalidrawAPI: async (exApi) => {
            excalidraw = exApi;
            const resp = await fetch("/excalidraw-data/" + noteId);
            const respJson = JSON.parse(await resp.text());
            const appState = respJson["appState"];
            exApi.updateScene({ elements: respJson["elements"], appState: appState });
            // initial scene is loaded from database, therefore already saved
            savedSceneNumber = ExcalidrawLib.getSceneVersion(excalidraw.getSceneElements());
            savedAppState = appState;
        },
        onChange: () => {
            // save at most every 1000 ms, and only if the user stopped working
            clearTimeout(saveTimeoutID);
            saveTimeoutID = setTimeout(checkAndMaybeSave, 1000);
        },
        UIOptions: {
            canvasActions: {
                export: false,
                loadScene: false,
                saveToActiveFile: false,
                saveAsImage: false2
            }
        }
      }),
    ),
  );
};

const excalidrawWrapper = document.getElementById("app");
const root = createRoot(excalidrawWrapper);
root.render(React.createElement(App));
