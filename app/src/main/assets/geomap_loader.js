"use strict";
import * as L from "leaflet";

// Leaflet docs: https://leafletjs.com/reference.html

var noteId = window.location.href.substring("https://trilium-notes.invalid/".length);

const container = document.querySelector(".geo-map-container");

var map;

function buildIcon(title, bxIconClass, color) {
        return L.divIcon({
            html: /*html*/`\
                <img class="icon" src="https://esm.sh/leaflet@1.9.4/dist/images/marker-icon.png" />
                <img class="icon-shadow" src="https://esm.sh/leaflet@1.9.4/dist/images/marker-shadow.png" />
                <span class="bx ${bxIconClass}" style="color: ${color}"></span>
                <span class="title-label">${title}</span>`,
            iconSize: [25, 41],
            iconAnchor: [12, 41]
        });
}

async function initCallback() {
        const resp = await fetch("/geomap-data/" + noteId);
        const j = JSON.parse(await resp.text());
        const prevView = localStorage.getItem("geoMap" + noteId);
        if (prevView) {
            const p = JSON.parse(prevView);
            map.setView([p["lat"], p["lng"]], p["zoom"]);
        } else {
            map.setView([j["view"]["center"]["lat"], j["view"]["center"]["lng"]], j["view"]["zoom"]);
        }

        // Restore markers.
        for (const pin of j.pins) {
            const marker = L.marker(L.latLng(pin.lat, pin.lng), {
                icon: buildIcon(pin.title, pin["iconClass"], pin["color"]),
                draggable: false,
                autoPan: true,
                autoPanSpeed: 5
            });
            marker.addTo(map);
            marker.on('click', () => {
                const p = {};
                p["lat"] = map.getCenter().lat;
                p["lng"] = map.getCenter().lng;
                p["zoom"] = map.getZoom();
                localStorage.setItem("geoMap" + noteId, JSON.stringify(p));
                api.activateNote(pin.noteId);
            });
        }

        // This fixes an issue with the map appearing cut off at the beginning, due to the container not being properly attached
        setTimeout(() => {
            //map.invalidateSize();
        }, 100);

        //const updateFn = () => this.spacedUpdate.scheduleUpdate();
        //map.on("moveend", updateFn);
        //map.on("zoomend", updateFn);
        //map.on("click", (e) => this.#onMapClicked(e));
}

        map = L.map(container, {
            worldCopyJump: true
        });

        initCallback();

        L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            detectRetina: true
        }).addTo(map);
