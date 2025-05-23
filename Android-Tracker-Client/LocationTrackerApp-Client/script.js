window.initAppFromConfig = function () {
    if (typeof window.APP_CONFIG === "undefined") {
        console.error("APP_CONFIG ainda não está disponível!");
        return;
    }
    let AUTH_TOKEN = window.APP_CONFIG.AUTH_TOKEN;
    let apiUrl = window.APP_CONFIG.apiUrl;

    startApp(AUTH_TOKEN, apiUrl);
}

function startApp(AUTH_TOKEN, apiUrl) {
    (async function () {
        await CefSharp.BindObjectAsync("comModule");

        const map = L.map('map', { preferCanvas: true }).setView([-22.1211461, -45.0588013], 15);
        map.getPane('markerPane').style.zIndex = 1000;
        map.createPane('circleMarkerPane');
        map.getPane('circleMarkerPane').style.zIndex = 650;

        const devices_list = document.getElementById("dispositivos");
        const device_context_menu = document.getElementById("device_context_menu");
        const rangeWrapperElement = document.querySelector("#rangeWrapper");
        const mapLayers = {};
        const openPopupID = map._popup?._source?.options?.id || null;

        let devices = [];
        let devices_changed = true;
        let readTimeStamps = [];
        let range = 300000;
        let actualDeviceID = null;
        let localDataMap = {};
        let activeRadiusCircle = null;

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
        }).addTo(map);

        await readHistoryIDs();

        setInterval(async () => {
            await requestAPI();
            if (actualDeviceID !== null) {
                selectDevice(actualDeviceID);
            }
        }, 5000);

        async function readHistoryIDs() {
            comModule.cefLog("readHistoryIDs running...")

            const ids = await comModule.readFileIDs();
            ids.forEach(id => {
                devices.push({ id: id, display: id });
            })

            devices_changed = true;
            await loadDevices();
        }

        async function requestAPI() {
            try {
                const localizacoes = await fetchAllLocations();

                let updated = false;

                localizacoes.forEach(loc => {
                    if (typeof loc.timestamp === 'string') loc.timestamp = Number(loc.timestamp);
                });

                localizacoes.forEach(loc => {
                    if (!devices.some(d => d.id == loc.phoneID)) {
                        devices.push({ id: loc.phoneID, display: loc.phoneID });
                        devices_changed = true;
                    }

                    if (!localDataMap[loc.phoneID]) {
                        localDataMap[loc.phoneID] = [];
                    }

                    if (!readTimeStamps.includes(loc.timestamp)) {
                        localDataMap[loc.phoneID].push(loc);
                        readTimeStamps.push(loc.timestamp);
                        updated = true;
                    }
                });

                if (updated && actualDeviceID) {
                    updateMap(actualDeviceID); 
                }

                await comModule.registerFile(JSON.stringify(localizacoes));
                await removeFromAPI();
                await loadDevices();
            } catch (error) {
                console.error(error);
            }
        }

        async function fetchAllLocations() {
            let allLocations = [];
            let page = 1;
            const limit = 100;
            let totalPages = 1;

            do {
                const response = await fetch(`${apiUrl}listarLocalizacoes?page=${page}&limit=${limit}`, {
                    headers: {
                        'Authorization': `Bearer ${AUTH_TOKEN}`
                    }
                });
                if (!response.ok) throw new Error("Erro ao buscar localizações");
                const result = await response.json();

                allLocations = allLocations.concat(result.data || []);
                totalPages = result.totalPages || 1;
                page++;
            } while (page <= totalPages);

            return allLocations;
        }


        function removeFromAPI() {
            fetch(apiUrl + "limparLocalizacoesLidas", {
                method: 'DELETE',
                headers: {
                    'Authorization': `Bearer ${AUTH_TOKEN}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ ids: readTimeStamps })
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Erro na requisição: ${response.status}`);
                    }
                    return response.text();
                })
                .then(data => comModule.cefLog("Resposta da API:", data))
                .catch(error => console.error("Erro ao limpar localizações lidas:", error));
        }

        function loadDevices() {
            comModule.cefLog("loadDevices running...");

            if (devices_changed) {
                devices_list.innerHTML = "";

                devices.forEach(device => {
                    let element = document.createElement('p');
                    element.innerHTML = device.display;
                    element.classList = "device";
                    element.id = device.id;
                    devices_list.appendChild(element);
                });

                let devices_elements = document.querySelectorAll(".device");

                devices_elements.forEach(element => {
                    element.addEventListener("contextmenu", function (event) {
                        event.preventDefault();
                        openDeviceContextMenu(event);
                    });
                    element.addEventListener("click", function (event) {
                        event.preventDefault();
                        selectDevice(element.id);
                    });
                });

                devices_changed = false;
            }
        }

        async function updateMap(deviceID) {
            actualDeviceID = deviceID;

            let locations = localDataMap[deviceID];

            if (!locations || !Array.isArray(locations) || locations.length === 0) {
                try {
                    let jsonString = await comModule.readFileAsJson(deviceID);
                    locations = JSON.parse(jsonString);
                    localDataMap[deviceID] = locations;
                } catch (error) {
                    console.error("Erro ao ler localizações do disco:", error);
                    await comModule.cefLog("Erro ao ler localizações do disco:", error.toString());
                    return;
                }
            }

            const limiteTimestamp = Date.now() - range;
            let filtered = locations.filter(loc =>
                loc.latitude && loc.longitude && loc.timestamp &&
                (range === 0 || loc.timestamp >= limiteTimestamp)
            );

            filtered.sort((a, b) => a.timestamp - b.timestamp);

            for (const key in mapLayers) {
                mapLayers[key].markers.forEach(m => map.removeLayer(m));
                mapLayers[key].lines.forEach(l => map.removeLayer(l));
                if (mapLayers[key].recentMarker) map.removeLayer(mapLayers[key].recentMarker);
            }

            mapLayers[deviceID] = { markers: [], lines: [], recentMarker: null };
            const currentLayers = mapLayers[deviceID];

            let openPopupLocID = null;
            if (map._popup && map._popup._source && map._popup._source.options) {
                openPopupLocID = map._popup._source.options.id;
            }

            currentLayers.markers.forEach(m => map.removeLayer(m));
            currentLayers.lines.forEach(l => map.removeLayer(l));
            if (currentLayers.recentMarker) map.removeLayer(currentLayers.recentMarker);

            currentLayers.markers = [];
            currentLayers.lines = [];
            currentLayers.recentMarker = null;

            if (filtered.length === 0) {
                await comModule.cefLog("Sem localizações recentes para:", deviceID);
                return;
            }

            const minTime = filtered[0].timestamp;
            const maxTime = filtered[filtered.length - 1].timestamp;
            const timeSpan = maxTime - minTime || 1;

            filtered.forEach(loc => {
                const latlng = [loc.latitude, loc.longitude];
                let fade = (loc.timestamp - minTime) / timeSpan;
                fade = Math.max(0, Math.min(fade, 1));

                const opacity = 0.1 + fade * 0.9;
                const hue = 180 + fade * 120;
                const colorHSL = `hsl(${hue}, 100%, 50%)`;

                const circle = L.circleMarker(latlng, {
                    pane: 'circleMarkerPane',
                    radius: 5,
                    color: colorHSL,
                    fillColor: colorHSL,
                    fillOpacity: opacity,
                    opacity: opacity,
                    id: loc.id
                }).bindPopup(`
                <b>ID:</b> ${loc.id}<br>
                <b>Data:</b> ${formatTimestamp(loc.timestamp)}<br>
                <b>PhoneID:</b> ${loc.phoneID}<br>
                <b>Latitude:</b> ${loc.latitude}<br>
                <b>Longitude:</b> ${loc.longitude}<br>
                <b>Precisão Horizontal:</b> ${loc.raio ?? "?"}m<br>
                <b>Altura:</b> ${loc.altitude ?? "?"}m<br>
                <b>Precisão Vertical:</b> ${loc.precisionAltitude ?? "?"}m<br>
            `);

                circle.on('click', () => {
                    if (activeRadiusCircle) {
                        map.removeLayer(activeRadiusCircle);
                        activeRadiusCircle = null;
                    }

                    if (typeof loc.raio === 'number' && loc.raio > 0) {
                        activeRadiusCircle = L.circle([loc.latitude, loc.longitude], {
                            radius: loc.raio,
                            color: 'blue',
                            weight: 1,
                            fillColor: 'blue',
                            fillOpacity: 0.1,
                            pane: 'circleMarkerPane' 
                        }).addTo(map);
                    }
                });

                circle.addTo(map);

                if (loc.id === openPopupLocID) {
                    setTimeout(() => circle.openPopup(), 0); 
                }

                currentLayers.markers.push(circle);
            });

            for (let i = 0; i < filtered.length - 1; i++) {
                const loc1 = filtered[i];
                const loc2 = filtered[i + 1];
                const latlng1 = [loc1.latitude, loc1.longitude];
                const latlng2 = [loc2.latitude, loc2.longitude];

                const avgTimestamp = (loc1.timestamp + loc2.timestamp) / 2;
                let fade = (avgTimestamp - minTime) / timeSpan;
                fade = Math.max(0, Math.min(fade, 1));

                const opacity = 0.1 + fade * 0.9;
                const hue = 180 + fade * 120;
                const colorHSL = `hsl(${hue}, 100%, 50%)`;

                const line = L.polyline([latlng1, latlng2], {
                    color: colorHSL,
                    weight: 3,
                    opacity: opacity
                }).addTo(map);

                currentLayers.lines.push(line);
            }

            const mostRecent = filtered[filtered.length - 1];
            const mostRecentLatLng = [mostRecent.latitude, mostRecent.longitude];

            const recentMarker = L.marker(mostRecentLatLng, { id: mostRecent.id })
                .bindPopup(`<b>ID:</b> ${mostRecent.id}<br>
                        <b>Data:</b> ${formatTimestamp(mostRecent.timestamp)}<br>
                        <b>PhoneID:</b> ${mostRecent.phoneID}<br>
                        <b>Latitude:</b> ${mostRecent.latitude}<br>
                        <b>Longitude:</b> ${mostRecent.longitude}<br>
                        <b>Precisão Horizontal:</b> ${mostRecent.raio ?? "?"}m<br>
                        <b>Altura:</b> ${mostRecent.altitude ?? "?"}m<br>
                        <b>Precisão Vertical:</b> ${mostRecent.precisionAltitude ?? "?"}m<br>`);

            recentMarker.on('click', () => {
                if (activeRadiusCircle) {
                    map.removeLayer(activeRadiusCircle);
                    activeRadiusCircle = null;
                }

                if (mostRecent.raio) {
                    activeRadiusCircle = L.circle([mostRecent.latitude, mostRecent.longitude], {
                        radius: mostRecent.raio,
                        color: 'blue',
                        weight: 1,
                        fillColor: 'blue',
                        fillOpacity: 0.1,
                        pane: 'circleMarkerPane'
                    }).addTo(map);
                }
            });

            recentMarker.addTo(map);

            if (mostRecent.id === openPopupLocID) {
                setTimeout(() => recentMarker.openPopup(), 0);
            }

            currentLayers.recentMarker = recentMarker;
            map.setView(mostRecentLatLng, map.getZoom());

            await comModule.cefLog("Mapa atualizado com dados de:", deviceID);
        }

        function selectDevice(deviceID) {
            actualDeviceID = deviceID;
            updateMap(deviceID);
        }

        function formatTimestamp(ts) {
            const date = new Date(ts);
            const dia = String(date.getDate()).padStart(2, '0');
            const mes = String(date.getMonth() + 1).padStart(2, '0');
            const ano = date.getFullYear();
            const hora = String(date.getHours()).padStart(2, '0');
            const minuto = String(date.getMinutes()).padStart(2, '0');
            const segundo = String(date.getSeconds()).padStart(2, '0');
            return `${dia}/${mes}/${ano} - ${hora}:${minuto}:${segundo}`;
        }

        function openDeviceContextMenu(event) {
            device_context_menu.style.left = `${event.clientX}px`
            device_context_menu.style.top = `${event.clientY}px`

            device_context_menu.style.visibility = `visible`;

            const esconderMenu = function (event) {
                comModule.cefLog([...device_context_menu.children], "  ", event.target)
                if (!([...device_context_menu.children].includes(event.target) || event.target == device_context_menu)) {
                    device_context_menu.style.visibility = `hidden`;
                    document.removeEventListener("click", esconderMenu);
                }
            };

            document.addEventListener("click", esconderMenu);
        }

        rangeWrapperElement.addEventListener("change", () => {
            range = parseInt(document.querySelector('input[name="range"]:checked')?.id || "300000");
            console.log("Intervalo selecionado (ms):", range);

            if (actualDeviceID !== null) {
                selectDevice(actualDeviceID);
            }
        });
    })();
}