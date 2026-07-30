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

<img width="300"  alt="Screenshot_20260728_181225" src="https://github.com/user-attachments/assets/1803ed1c-a650-46cb-afad-6550ef87416b" />
<img width="300" alt="Screenshot_20260728_181257" src="https://github.com/user-attachments/assets/9a1b75d3-e27d-4257-b4db-bb1aff5e4e37" />
<img width="300"  alt="Screenshot_20260728_180242" src="https://github.com/user-attachments/assets/d00f51d1-0709-40c2-bd87-65d9bb1b6689" />
<img width="300" alt="Screenshot_20260728_175252" src="https://github.com/user-attachments/assets/d9122455-ef40-4f27-8095-229d75eb29cd" />
<img width="300" alt="Screenshot_20260728_174511" src="https://github.com/user-attachments/assets/a4c2286f-78b6-45c5-a381-bc88e0f095b0" />
<img width="300" alt="Screenshot_20260728_174408" src="https://github.com/user-attachments/assets/b8ed8ba9-d929-45dc-b4e6-ae94b045f6e9" />

Libraries 

https://github.com/readium/kotlin-toolkit

https://github.com/tomroush/pdfbox-android


 [![Telegram](https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/LightReaderApp)
