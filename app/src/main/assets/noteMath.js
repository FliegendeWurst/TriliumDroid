import katex from "katex";
import "katex/contrib/mhchem";
import { default as renderMathInElement } from "katex/contrib/auto-render";

document.querySelectorAll("span.math-tex").forEach(it => {
    renderMathInElement(it);
});
