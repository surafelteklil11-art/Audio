# Audio PDF Reader

Open **PDF Reader** immediately below **Checkers** in Audio's drawer. It works without Internet access. After the user enables file access, it discovers PDF files in shared phone storage and mounted SD cards automatically and reads them in place. The + button still uses the Android document picker for optional private imports. Device originals are never renamed, edited or deleted by these tools.

## Library and reading

- Home with folders, PDF/folder filters, name search, sorting and multiple selection. Move, rename, favorite, export, share and print documents.
- Recent remembers the last page; Favorite lists starred documents. Recycle bin supports restoration and separately confirmed permanent deletion of library copies.
- Dark/light library appearance and optional keep-screen-on. The document screens own their palette so the Audio music background cannot overwrite it.
- Native PDF rendering runs on a worker, with bounded bitmap sizes. Page arrows, page-number jump, pinch zoom, drag pan and double-tap zoom. Page night mode is separate from library appearance.
- Search selectable text; password prompts for protected files. Passwords remain in memory only. Decrypted display copies are private temporary files and are removed when the reader session closes.
- Add text, draw ink/highlights or a handwritten signature; save a separate annotated PDF. Marks and the current page survive rotation while the process lives. Unfinished marks are not persisted after process termination. A drawn signature is not a cryptographic digital signature.

## Tools

- Images to PDF (including camera capture and EXIF orientation), and Unicode text to paginated PDF.
- PDF pages to PNG ZIP, or a combined long PNG (up to 20 pages and bounded image dimensions).
- Extract/edit text creates a new text PDF. It does **not** replace arbitrary text in the original page layout. Images and original formatting are not retained in this operation. OCR and Office formats are not included.
- Merge in selection order; split/extract a page list such as `1,3-5`; reorder/keep pages such as `3,1,2`; rotate pages clockwise. Omitted pages are removed only from the new copy. These tools retain source PDF content rather than rasterizing it.
- Compression creates JPEG-based image pages at reduced resolution. It loses selectable text and interactive content, and a smaller output is not guaranteed. The original is retained and the UI explains this before conversion.
- Lock with AES-256; unlock requires the owner password. Document extraction/editing/printing permission checks are enforced where applicable. Unlock a permitted copy before using protected files in conversion tools.
- Share via temporary Android URI grants, export through the system picker, and print with selected page ranges.

Imports are limited to 512 MB each. Merge supports 2–30 PDFs and up to 2,000 pages; image creation supports up to 100 images; long-image export supports up to 20 pages. These bounds prevent unbounded bitmap allocations on phones. Large files still depend on available storage and memory. Camera scanning captures a photo; it does not perform automatic edge detection or OCR. Opening cloud-provider documents can require that provider's Internet connection to download them once; local copies are read offline thereafter.

## Dependencies and validation

Document manipulation uses [PDFBox-Android 2.0.27.0](https://github.com/TomRoush/PdfBox-Android), an Apache-2.0-licensed port of Apache PDFBox. Rendering uses Android [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer). No PDF account or cloud processing service is used.

Regression coverage includes immutable imports, invalid-file rollback, folder cycles, atomic bulk moves, trash/restore behavior, page ordering and rotation, merging/text preservation, password round trips, bounded native rendering, text/image/annotation outputs, PNG/ZIP exports, dark/light screens, background isolation, reader state across recreation and annotated-copy saving. CI also runs the existing Audio test suite and builds the APK. Physical-device camera/provider/printing integration remains a device validation step.

PDF UI and document operations run as Android instrumentation tests on API 33 and 35, alongside the existing 101 JVM Audio regression tests. The PDF library navigation, background isolation, resume and recreation checks use the real Android runtime; Robolectric does not fully emulate the native PDF APIs or this worker-backed screen on modern SDKs. Device screenshots are retained as CI artifacts.

## Device discovery and revised navigation

The compact top app bar contains PDF Reader beside the menu button. The menu opens a left-side DrawerLayout with swipe/back dismissal. Tools use four equal columns and native vector-style line icons.

On Android 11+, **Find PDFs on this phone** explains and opens Android’s **All files access** setting for Audio. On Android 7–10 it requests storage read permission. Scanning starts automatically on returning from permission settings and whenever the library resumes; **File access & refresh** repeats it. Permission denial/revocation retains manual importing and private-library reading. This uses [Android’s documented all-files access](https://developer.android.com/training/data-storage/manage-all-files); Google Play distribution would require its applicable all-files access review.

The scanner indexes .pdf names case-insensitively on a worker, without copying or rendering every document. It scans shared storage, including Android/media, while excluding hidden folders, Android/data, Android/obb and symbolic links. App-private documents and cloud-only files are not discoverable. Scanning is bounded at 150,000 filesystem entries, 20,000 PDFs and 24 folder levels and reports when limited. Metadata/history survives rescans and temporarily unavailable volumes. Missing documents are hidden.

Device entries show their folder and an On device label. They can be read, searched, favorited, shared, printed and used by PDF tools. **Save library copy** creates a separate copy for rename/move/recycle-bin operations. Sharing a device PDF creates a temporary export on a worker and grants only that exported file; broad storage roots are not exposed through FileProvider. Conversion and annotation results are new private library documents.
