# Project rules

## Builds

- Never run Gradle or any compiler in this repository (`./gradlew ...`, `gradle`, `javac`, `kotlinc`). The user builds and verifies compilation himself.
- Do not start builds in the background either.
- Make code changes only; leave verification to the user.

## Перенос функций из других проектов

- Сравнивай поведение, а не только названия и настройки.
- Проследи путь от ввода или события до результата. Проверь отмену события, смену цели и мира, а также взаимодействие с соседними модулями там, где это применимо.
- Если поведение упрощено или отличается от оригинала, явно укажи, что изменилось и почему.
- Для каждого нетривиального изменения добавь тест или опиши конкретный сценарий ручной проверки. Gradle и компиляторы не запускай.
