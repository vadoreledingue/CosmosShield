// Génère les assets de l'app Android à partir de web/index.html
// Usage : npm install && npm run build
import { execSync } from 'node:child_process';
import { cpSync, mkdirSync, copyFileSync } from 'node:fs';

const out = '../app/src/main/assets/www';
mkdirSync(out + '/fonts', { recursive: true });
execSync(`npx tailwindcss -c tailwind.config.js -i input.css -o ${out}/app.css --minify`, { stdio: 'inherit' });
copyFileSync('index.html', out + '/index.html');
copyFileSync('node_modules/lucide/dist/umd/lucide.min.js', out + '/lucide.min.js');
copyFileSync('node_modules/@fontsource-variable/inter/files/inter-latin-wght-normal.woff2', out + '/fonts/inter-latin-wght-normal.woff2');
copyFileSync('node_modules/@fontsource-variable/jetbrains-mono/files/jetbrains-mono-latin-wght-normal.woff2', out + '/fonts/jetbrains-mono-latin-wght-normal.woff2');
console.log('Assets générés dans', out);
