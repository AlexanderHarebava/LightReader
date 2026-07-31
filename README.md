***Light Reader***

<img width="84" height="84" alt="LightReaderIcon" src="https://github.com/user-attachments/assets/29d1592c-badb-4c26-adaf-f9816218ccbd" /> 

Light Reader is a modern mobile e-book reader for the Android platform, built on the **Readium Kotlin Toolkit**. The project combines core reading functionality for various book formats, a built-in library, and advanced artificial intelligence (AI) features.

---

## Key Features and Functionality

### 1. Reading and Supported Formats
*   **Supported Formats:** EPUB, PDF, FB2, TXT, DOCX.
*   **Selectable Text in PDF**
*   **Built-in Converters:** Automatic conversion of FB2, TXT, and DOCX files into EPUB format for a comfortable reading experience.
*   **Display Customization:** Adjustment of fonts, text size, and themes (light/dark); includes a quick settings menu and adaptation to user visual preferences.
*   **Navigation and Bookmarks:** Saving reading progress, creating bookmarks, text highlighting, and table of contents (outline) navigation.
*   **Text-to-Speech (TTS):** Text-to-speech narration with playback controls and voice customization options.
*   **Search:** In-book text search functionality.

### 2. Bookshelf and Library Management
*   **Interface:** Modern book list display powered by Jetpack Compose.
*   **Filtering and Tags:** Categorizing books with statuses such as "Favorites," "Want to Read," and "Read."
*   **Sorting and Metadata:** Displaying file size, date added, format type, and reading progress percentage.
*   **Cover Generation:** Automatic generation of text-based and graphical covers when original artwork is missing.

### 3. AI Assistant Integration
The application features an AI assistant capable of analyzing book texts, answering questions, and conducting context-aware conversations based on the loaded content.

*   **Supported AI Providers:**
    *   **Google Gemini** (direct integration with full-text analysis).
    *   **OpenAI** (models).
    *   **DeepSeek** (`deepseek-chat` models and local/cloud variants).
    *   **OpenRouter** (access to a wide range of third-party models).
*   **RAG (Retrieval-Augmented Generation):**
    *   Chunking of book text with overlap preservation.
    *   Generation and caching of text vector embeddings.
    *   Retrieval of the most relevant book excerpts via cosine similarity to provide accurate AI answers regarding the content.
*   **Chat Management:**
    *   Saving conversation history in JSON format.
    *   Creating new sessions, renaming, and deleting chats.
    *   Exporting dialogs to `.txt` format and sharing capabilities.
    *   Attaching specific books from the library to the current chat session.

<img width="300"  alt="Screenshot_20260730_165716" src="https://github.com/user-attachments/assets/e4287b41-dc55-4a4f-8adb-53177bcd0afa" />
<img width="300"  alt="Screenshot_20260730_165351" src="https://github.com/user-attachments/assets/51f477e7-d67d-4095-8dc5-ef02ed62e8fb" />
<img width="300"  alt="Screenshot_20260730_165909" src="https://github.com/user-attachments/assets/25b9e021-d5bb-4a44-b29c-9e93108aee6d" />
<img width="300"  alt="Screenshot_20260730_165733" src="https://github.com/user-attachments/assets/5a18f25f-df58-40db-b04a-810e76f78abd" />
<img width="300"  alt="Screenshot_20260730_165301" src="https://github.com/user-attachments/assets/43f8b273-ae1c-4536-9e8e-10eeef5dc10f" />
<img width="300"  alt="Screenshot_20260730_165238" src="https://github.com/user-attachments/assets/7e4aeed1-cd4e-4ef5-946c-2013821341ca" />


Libraries 

https://github.com/readium/kotlin-toolkit

https://github.com/tomroush/pdfbox-android


 [![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/LightReaderApp)
