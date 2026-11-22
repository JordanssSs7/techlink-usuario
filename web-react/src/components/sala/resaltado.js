import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import c from 'highlight.js/lib/languages/c';
import cpp from 'highlight.js/lib/languages/cpp';
import csharp from 'highlight.js/lib/languages/csharp';
import css from 'highlight.js/lib/languages/css';
import go from 'highlight.js/lib/languages/go';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import kotlin from 'highlight.js/lib/languages/kotlin';
import php from 'highlight.js/lib/languages/php';
import python from 'highlight.js/lib/languages/python';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';

// HU-08: resaltado de código del chat. Solo se cargan los lenguajes que ofrece la sala (los mismos que acepta el backend en
// SalaMensajeService.LENGUAJES).
const REGISTRO = { bash, c, cpp, csharp, css, go, java, javascript, json, kotlin, php, python, sql, typescript, xml };
Object.entries(REGISTRO).forEach(([nombre, definicion]) => hljs.registerLanguage(nombre, definicion));

// "html" se resalta como xml.
const ALIAS = { html: 'xml' };

export const LENGUAJES = [
  ['texto', 'Texto plano'],
  ['java', 'Java'],
  ['javascript', 'JavaScript'],
  ['typescript', 'TypeScript'],
  ['python', 'Python'],
  ['sql', 'SQL'],
  ['html', 'HTML'],
  ['css', 'CSS'],
  ['c', 'C'],
  ['cpp', 'C++'],
  ['csharp', 'C#'],
  ['php', 'PHP'],
  ['json', 'JSON'],
  ['bash', 'Bash'],
  ['xml', 'XML'],
  ['kotlin', 'Kotlin'],
  ['go', 'Go']
];

export function nombreDeLenguaje(id) {
  return LENGUAJES.find(([valor]) => valor === id)?.[1] ?? 'Texto plano';
}

function escapar(texto) {
  return texto.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// Devuelve HTML ya escapado con las marcas de color. Si el lenguaje no se conoce, devuelve el texto escapado sin colores.
export function resaltar(codigo, lenguaje) {
  const nombre = ALIAS[lenguaje] ?? lenguaje;
  if (!nombre || !hljs.getLanguage(nombre)) return escapar(codigo);
  try {
    return hljs.highlight(codigo, { language: nombre, ignoreIllegals: true }).value;
  } catch {
    return escapar(codigo);
  }
}
