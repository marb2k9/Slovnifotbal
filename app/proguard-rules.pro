# Pravidla pro R8 (release build).
# Aplikace nepoužívá reflexi ani serializaci přes reflexi; knihovny AndroidX a Compose
# přinášejí vlastní pravidla. Výčtové typy se ukládají přes vlastní stabilní klíče (key),
# takže přejmenování tříd nevadí.

# Zachování čísel řádků pro čitelnější chybová hlášení
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
