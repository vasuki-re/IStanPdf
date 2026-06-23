<div align="center">

  <img src="assets/icon.png" width="160" height="160" alt="IStanPdf Logo" style="border-radius: 22%">

  <h1>IStanPdf</h1>

  <p align="center">
    <strong>Offline PDF & DOCX operations. No paywalls. No internet. No nonsense.</strong>
    <br />
    <em>Built to counter freemium online pdf and docx conversion services.</em>
  </p>

  <p align="center">
    <a href="#-showcase"><b>Showcase</b></a> •
    <a href="#-features"><b>Features</b></a> •
    <a href="#-download"><b>Download</b></a> •
    <a href="#-known-issues"><b>Known Issues</b></a> •
    <a href="#-contributing"><b>Contributing</b></a> •
    <a href="#-donate"><b>Donate</b></a>
  </p>

</div>

---

**IStanPdf** is an offline Android utility for PDF and DOCX operations. There are no subscriptions, internet requirements, or file size limits. I built this as a fast, local alternative to freemium online tools like iLovePDF and Smallpdf. Everything runs directly on your device.

---

## 📸 Showcase

<div align="center">

<img src="assets/ui.jpg" alt="Home Screen" width="30%" />
<img src="assets/merge.jpg" alt="Merge PDF" width="30%" />
<img src="assets/split.jpg" alt="Split PDF" width="30%" />
<img src="assets/modify_pdf.jpg" alt="Modify PDF" width="30%" />
<img src="assets/img2pdf.jpg" alt="Image to PDF" width="30%" />
<img src="assets/rmpage_docx.jpg" alt="Remove Pages from DOCX" width="30%" />
</div>
<div align="left">
<img src="assets/reorder_docx.jpg" alt="Reorder DOCX" width="30%" />
</div>

---

## ✨ Features

<div align="center">

<table>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>📄 PDF Tools</h3>
        <ul>
          <li><b>Merge PDF:</b> Combine multiple PDF files into one.</li>
          <li><b>Split PDF:</b> Extract pages by specifying a page range.</li>
          <li><b>Remove Pages:</b> Delete specific pages from a PDF.</li>
          <li><b>Reorder Pages:</b> Rearrange pages within a PDF.</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>🔄 Conversions</h3>
        <ul>
          <li><b>Images to PDF:</b> Convert one or more images into a single PDF document.</li>
          <li><b>PDF to Image:</b> Extract PDF pages and save them as images.</li>
        </ul>
      </div>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <div align="left">
        <h3>📝 DOCX Tools</h3>
        <ul>
          <li><b>Remove Pages:</b> Delete specific pages from a DOCX file.</li>
          <li><b>Reorder Pages:</b> Rearrange pages within a DOCX file.</li>
        </ul>
      </div>
    </td>
    <td width="50%" valign="top">
      <div align="left">
        <h3>🔒 Privacy & Offline</h3>
        <ul>
          <li>Fully offline — your files never leave your device.</li>
          <li>No account required.</li>
          <li>No ads, no paywalls, no upload limits.</li>
        </ul>
      </div>
    </td>
  </tr>
</table>

</div>

---

## 📥 Download

<div align="center">

<table>
  <thead>
    <tr>
      <th align="center">GitHub Releases</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center">
        <a href="https://github.com/vasuki-re/IStanPdf/releases/latest">
          <img src="https://img.shields.io/badge/Download-GitHub-6366f1?style=for-the-badge&logo=github&labelColor=1e1e2e" height="50" alt="Download on GitHub" />
        </a>
      </td>
    </tr>
  </tbody>
</table>

</div>

---

## ⚠️ Known Issues

- **DOCX Remove Pages:** Some DOCX files with structural issues fail to save during the "Remove Pages" operation.
  - **Workaround:** Use "Reorder Pages" instead - it internally converts the DOCX to PDF to bypass the limitation.
  - **Caveat:** If you later convert the resulting PDF back to DOCX, some elements may no longer be editable.

- **App Size:** IStanPdf is larger than a typical utility app because it bundles LibreOffice binaries, which power the DOCX operations.

- **UI:** The interface is functional but a bit rough right now. I'm planning some micro-optimizations to make it more accessible.
- **Monolith Architecture:** Core operations currently live in a singleton `MainActivity`. I plan to refactor this into a modular architecture in future updates to make debugging and maintenance easier.

---

## 📋 TODOs

- [ ] **Refactor Architecture:** Refactor code from monolith to a better architecture for easier debugging.
- [ ] **Compress PDF:** Introduce Compress PDF feature.
- [ ] **Optimize DOCX:** Optimize DOCX operations.
- [ ] **Auto DOCX Repair:** Introduce auto DOCX Repair feature to fix structural issues in DOCX making it difficult to be saved.

---

## 💡 Why IStanPdf?

As a CS student, I regularly needed PDF and DOCX tools but found most online options too slow or restricted by paywalls. I couldn't find a lightweight offline alternative, so I built this.

### Why Vibecoding?

Vibecoding doesn't mean you don't know how it's done. I am a 1st-year CS Engineering Student, and I am actively learning. My ultimate aim is to manually code everything and eventually ditch vibecoding. 

| Pros | Cons |
| :--- | :--- |
| Fast prototyping | Can introduce bugs |
| Rapid development | Extra debugging time |
| Helpful for learning | Model hallucinations |

---

## 🛠️ Development

| Tool | Purpose |
| :--- | :--- |
| **Antigravity 2.0** | Development |
| **Codex** | Development |
| **ChatGPT Image Generation** | UI Design |

---

## 🤝 Contributing

Found a bug or have a feature idea? Feel free to open an issue or submit a PR. Let's fix it together!

[![GitHub Issues](https://img.shields.io/github/issues/vasuki-re/IStanPdf?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github)](https://github.com/vasuki-re/IStanPdf/issues)

---

## ☕ Donate

If IStanPdf has been useful to you, consider supporting its development!

<div align="left">
  <a href="https://ko-fi.com/ramakanthgacharya">
    <img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="Support on Ko-fi" />
  </a>
</div>

<br>

---

<br>

## 🏆 Credits

- **iLovePDF & Smallpdf** - For the UI and design inspiration.
- **LibreOffice** - The engine under the hood that makes all the DOCX operations possible.

<br>

---

<br>

<div align="center">
  <p><b>If you find this app useful, consider giving it a ⭐</b></p>
</div>
