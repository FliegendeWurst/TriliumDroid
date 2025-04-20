"use strict";

var glob = {
    getComponentByEl: (x) => {
        console.log("getComponentByEl?", x["tagName"], x["className"]);
        return null;
    }
};
var noteId = window.location.href.substring("https://trilium-notes.invalid/note-editable#".length);

/* referenc: https://github.com/TriliumNext/Notes/blob/develop/src/public/app/services/link.ts#L9-L15 */
const ALLOWED_PROTOCOLS = [
    'http', 'https', 'ftp', 'ftps', 'mailto', 'data', 'evernote', 'file', 'facetime', 'gemini', 'git',
    'gopher', 'imap', 'irc', 'irc6', 'jabber', 'jar', 'lastfm', 'ldap', 'ldaps', 'magnet', 'message',
    'mumble', 'nfs', 'onenote', 'pop', 'rmi', 's3', 'sftp', 'skype', 'sms', 'spotify', 'steam', 'svn', 'udp',
    'view-source', 'vlc', 'vnc', 'ws', 'wss', 'xmpp', 'jdbc', 'slack', 'tel', 'smb', 'zotero', 'geo',
    'mid'
];
/* reference: https://github.com/TriliumNext/Notes/blob/v0.93.0/src/public/app/widgets/type_widgets/ckeditor/config.ts#L10-L102 */
function buildConfig() {
    return {
        image: {
            styles: {
                options: [
                    "inline",
                    "alignBlockLeft",
                    "alignCenter",
                    "alignBlockRight",
                    "alignLeft",
                    "alignRight"
                    //"full", // full and side are for BC since the old images have been created with these styles
                    //"side" // TODO
                ]
            },
            resizeOptions: [
                {
                    name: "imageResize:original",
                    value: null,
                    icon: "original"
                },
                {
                    name: "imageResize:25",
                    value: "25",
                    icon: "small"
                },
                {
                    name: "imageResize:50",
                    value: "50",
                    icon: "medium"
                },
                {
                    name: "imageResize:75",
                    value: "75",
                    icon: "medium"
                }
            ],
            toolbar: [
                // Image styles, see https://ckeditor.com/docs/ckeditor5/latest/features/images/images-styles.html#demo.
                "imageStyle:inline",
                "imageStyle:alignCenter",
                {
                    name: "imageStyle:wrapText",
                    title: "Wrap text",
                    items: ["imageStyle:alignLeft", "imageStyle:alignRight"],
                    defaultItem: "imageStyle:alignRight"
                },
                {
                    name: "imageStyle:block",
                    title: "Block align",
                    items: ["imageStyle:alignBlockLeft", "imageStyle:alignBlockRight"],
                    defaultItem: "imageStyle:alignBlockLeft"
                },
                "|",
                "imageResize:25",
                "imageResize:50",
                "imageResize:original",
                "|",
                "toggleImageCaption"
            ],
            upload: {
                types: ["jpeg", "png", "gif", "bmp", "webp", "tiff", "svg", "svg+xml", "avif"]
            }
        },
        heading: {
            options: [
                { model: "paragraph", title: "Paragraph", class: "ck-heading_paragraph" },
                // // heading1 is not used since that should be a note's title
                { model: "heading2", view: "h2", title: "Heading 2", class: "ck-heading_heading2" },
                { model: "heading3", view: "h3", title: "Heading 3", class: "ck-heading_heading3" },
                { model: "heading4", view: "h4", title: "Heading 4", class: "ck-heading_heading4" },
                { model: "heading5", view: "h5", title: "Heading 5", class: "ck-heading_heading5" },
                { model: "heading6", view: "h6", title: "Heading 6", class: "ck-heading_heading6" }
            ]
        },
        table: {
            contentToolbar: ["tableColumn", "tableRow", "mergeTableCells", "tableProperties", "tableCellProperties", "toggleTableCaption"]
        },
        list: {
            properties: {
                styles: true,
                startIndex: true,
                reversed: true
            }
        },
        link: {
            defaultProtocol: "https://",
            allowedProtocols: ALLOWED_PROTOCOLS
        },
        // This value must be kept in sync with the language defined in webpack.config.js.
        language: "en"
    };
}
/* reference: https://github.com/TriliumNext/Notes/blob/v0.93.0/src/public/app/widgets/type_widgets/ckeditor/config.ts#L5-L8 */
const TEXT_FORMATTING_GROUP = {
    label: "Text formatting",
    icon: "text"
};
/* reference: https://github.com/TriliumNext/Notes/blob/v0.93.0/src/public/app/widgets/type_widgets/ckeditor/config.ts#L104-L185 */
function buildToolbarConfig(isClassicToolbar) {
    return buildMobileToolbar();
}

function buildMobileToolbar() {
    const classicConfig = buildClassicToolbar(false);
    const items = [];

    for (const item of classicConfig.toolbar.items) {
        if (typeof item === "object" && "items" in item) {
            for (const subitem of item.items) {
                items.push(subitem);
            }
        } else {
            items.push(item);
        }
    }

    return {
        ...classicConfig,
        toolbar: {
            ...classicConfig.toolbar,
            items
        }
    };
}

function buildClassicToolbar(multilineToolbar) {
    // For nested toolbars, refer to https://ckeditor.com/docs/ckeditor5/latest/getting-started/setup/toolbar.html#grouping-toolbar-items-in-dropdowns-nested-toolbars.
    return {
        toolbar: {
            items: [
                "heading",
                "fontSize",
                "|",
                "bold",
                "italic",
                {
                    ...TEXT_FORMATTING_GROUP,
                    items: ["underline", "strikethrough", "|", "superscript", "subscript", "|", "kbd"]
                },
                "|",
                "fontColor",
                "fontBackgroundColor",
                "removeFormat",
                "|",
                "bulletedList",
                "numberedList",
                "todoList",
                "|",
                "blockQuote",
                "admonition",
                "insertTable",
                "|",
                "code",
                "codeBlock",
                "|",
                "footnote",
                {
                    label: "Insert",
                    icon: "plus",
                    items: ["imageUpload", "|", "link", "internallink", "includeNote", "|", "specialCharacters", "math", "mermaid", "horizontalLine", "pageBreak"]
                },
                "|",
                "outdent",
                "indent",
                "|",
                "markdownImport",
                "cuttonote",
                "findAndReplace"
            ],
            shouldNotGroupWhenFull: multilineToolbar
        }
    };
}

/* reference: https://github.com/TriliumNext/Notes/blob/v0.93.0/src/public/app/widgets/type_widgets/editable_text.ts */
var editor = document.querySelector(".note-detail-editable-text-editor");
const isClassicEditor = true;
const editorClass = isClassicEditor ? CKEditor.DecoupledEditor : CKEditor.BalloonEditor;
console.log("new Watchdog");
const watchdog = new CKEditor.EditorWatchdog(editorClass, {
    // An average number of milliseconds between the last editor errors (defaults to 5000).
    // When the period of time between errors is lower than that and the crashNumberLimit
    // is also reached, the watchdog changes its state to crashedPermanently, and it stops
    // restarting the editor. This prevents an infinite restart loop.
    minimumNonErrorTimePeriod: 5000,
    // A threshold specifying the number of errors (defaults to 3).
    // After this limit is reached and the time between last errors
    // is shorter than minimumNonErrorTimePeriod, the watchdog changes
    // its state to crashedPermanently, and it stops restarting the editor.
    // This prevents an infinite restart loop.
    crashNumberLimit: 3,
    // A minimum number of milliseconds between saving the editor data internally (defaults to 5000).
    // Note that for large documents, this might impact the editor performance.
    saveInterval: 5000
});
console.log("on stateChange");
watchdog.on("stateChange", () => {
    const currentState = watchdog.state;

    if (!["crashed", "crashedPermanently"].includes(currentState)) {
        return;
    }

    console.log("CKEditor crash logs:");
    watchdog.crashes.forEach((crashInfo) => console.log(crashInfo));

    if (currentState === "crashedPermanently") {
        // TODO
        // dialogService.info(`Editing component keeps crashing. Please try restarting Trilium. If problem persists, consider creating a bug report.`);

        watchdog.editor.enableReadOnlyMode("crashed-editor");
    }
});
var updateTimeoutID = -1;
function scheduledUpdate() {
    api.updateExcalidrawNote(noteId, watchdog.editor.getData());
}
function scheduleUpdate() {
    clearTimeout(updateTimeoutID);
    updateTimeoutID = setTimeout(scheduledUpdate, 1000);
}
console.log("setCreator");
watchdog.setCreator(async (elementOrData, editorConfig) => {
    const finalConfig = {
        ...editorConfig,
        ...buildConfig(),
        ...buildToolbarConfig(isClassicEditor),
        htmlSupport: {
            // TODO: allow: JSON.parse(options.get("allowedHtmlTags")),
            allow: ["h1","h2","h3","h4","h5","h6","blockquote","p","a","ul","ol","li","b","i","strong","em","strike","s","del","abbr","code","hr","br","div","table","thead","caption","tbody","tfoot","tr","th","td","pre","section","img","figure","figcaption","span","label","input","details","summary","address","aside","footer","header","hgroup","main","nav","dl","dt","menu","bdi","bdo","dfn","kbd","mark","q","time","var","wbr","area","map","track","video","audio","picture","del","ins","en-media","acronym","article","big","button","cite","col","colgroup","data","dd","fieldset","form","legend","meter","noscript","option","progress","rp","samp","small","sub","sup","template","textarea","tt"],
            styles: true,
            classes: true,
            attributes: true
        }
    };

    // TODO: this.note?.getLabelValue("language");
    finalConfig.language = {
        ui: "en",
        content: "en"
    };

    const editor = await editorClass.create(elementOrData, finalConfig);

    const notificationsPlugin = editor.plugins.get("Notification");
    notificationsPlugin.on("show:warning", (evt, data) => {
        const title = data.title;
        const message = data.message.message;

        /* TODO:
        if (title && message) {
            toast.showErrorTitleAndMessage(data.title, data.message.message);
        } else if (title) {
            toast.showError(title || message);
        }
        */
        console.log(title);
        console.log(message);

        evt.stop();
    });

    // TODO: await initSyntaxHighlighting(editor);

    if (isClassicEditor) {
        let $classicToolbarWidget = document.querySelector(".classic-toolbar-widget");

        // TODO: $classicToolbarWidget.empty();
        if ($classicToolbarWidget) {
            $classicToolbarWidget.innerHTML = "";
            $classicToolbarWidget.appendChild(editor.ui.view.toolbar.element);
        }

        // Reposition all dropdowns to point upwards instead of downwards.
        // See https://ckeditor.com/docs/ckeditor5/latest/examples/framework/bottom-toolbar-editor.html for more info.
        const toolbarView = editor.ui.view.toolbar;
        for (const item of toolbarView.items) {
            if (!("panelView" in item)) {
                continue;
            }

            item.on("change:isOpen", () => {
                if ( !item.isOpen ) {
                    return;
                }

                item.panelView.position = item.panelView.position.replace("s", "n");
            });
        }
    }

    editor.model.document.on("change:data", () => scheduleUpdate());

    return editor;
});

async function initialize() {
console.log("in initialize");
await watchdog.create(editor, {
    placeholder: "editable_text.placeholder", // TODO
    /*
    mention: {}, // TODO: mentionSetup,
    codeBlock: {
        languages: [] // TODO buildListOfLanguages()
    },
    math: {
        engine: "katex",
        outputType: "span", // or script
        lazyLoad: async () => {}, // TODO await libraryLoader.requireLibrary(libraryLoader.KATEX),
        forceOutputType: false, // forces output to use outputType
        enablePreview: true // Enable preview view
    },
    mermaid: {
        lazyLoad: async () => {}, // TODO (await import("mermaid")).default,
        config: {} // TODO getMermaidConfig()
    }
    */
});
const resp = await fetch("/note-raw/" + noteId);
const html = await resp.text();
watchdog.editor.setData(html);
}

initialize();
