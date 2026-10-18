# Kotlin Users API — контрольная точка по Kotlin

REST API для управления пользователями. Написан на **Kotlin 2.2.10**, сервер — **Ktor 3.2.3 / Netty**, сборка — **Maven**. JSON обрабатывается через **ContentNegotiation + kotlinx.serialization**.

Проект переделан из консольной программы под требования задания. PostgreSQL не нужен: пользователи хранятся в памяти, при перезапуске список очищается. При первом запуске список пустой.

## Соответствие ТЗ

| Требование | Реализация |
| --- | --- |
| Сервер на Ktor | `Main.kt`: `embeddedServer(Netty)` |
| GET, POST, DELETE | `UserRoutes.kt`: маршруты `/users` и `/users/{id}` |
| JSON-сериализация | `Application.kt`: `ContentNegotiation { json(...) }`, модели с `@Serializable` |
| Query-параметры | `GET /users?name=Ан&limit=10&offset=0` |
| Path-параметры | `GET /users/{id}`, `DELETE /users/{id}` |
| Ответы и HTTP-коды | 200, 201, 204, 400, 404, 405, 409, 415, 500 |
| Публичный Git-репозиторий | После загрузки по инструкции ниже нужно сдать публичную ссылку |

## Запуск

Нужен **JDK 17 или новее**; для воспроизводимой сборки рекомендуется JDK 17. Проверьте `java -version`. В `JAVA_HOME` должен быть путь к JDK. Устанавливать Maven отдельно не нужно: включён Maven Wrapper. При первой сборке нужен интернет для загрузки Maven и зависимостей.

Если в Windows выбрана старая Java, задайте JDK для текущего PowerShell (укажите свой установленный путь):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Windows PowerShell, из папки проекта:

```powershell
.\mvnw.cmd clean verify
java -jar target/kotlin-users-api.jar
```

Linux / macOS:

```bash
sh ./mvnw clean verify
java -jar target/kotlin-users-api.jar
```

Сервер доступен по адресу **http://localhost:8080**. Для остановки нажмите `Ctrl+C` в терминале сервера. По умолчанию сервер слушает `0.0.0.0:8080`. Порт можно поменять переменной окружения `PORT`:

```powershell
$env:PORT = '9090'
java -jar target/kotlin-users-api.jar
```

В IntelliJ IDEA откройте папку как Maven-проект, выберите JDK 17 и дождитесь загрузки зависимостей. Затем запустите `main()` из `src/main/kotlin/Main.kt`.

## Маршруты

| Метод | URL | Результат |
| --- | --- | --- |
| GET | `/` | `200 OK`: сведения об API |
| GET | `/users` | `200 OK`: массив пользователей, при отсутствии — `[]` |
| GET | `/users?name=Ан&limit=10&offset=0` | `200 OK`: отфильтрованный список |
| GET | `/users/{id}` | `200 OK`: пользователь; `404`, если его нет |
| POST | `/users` | `201 Created`: созданный пользователь и заголовок `Location` |
| DELETE | `/users/{id}` | `204 No Content`: удалён, тело пустое; `404`, если его нет |

Query-параметры необязательны. `name` — непустая часть имени без учёта регистра; `limit` — целое число от 1 до 100, по умолчанию 20; `offset` — неотрицательное целое число, по умолчанию 0. Сначала применяется фильтр, затем смещение и ограничение количества. Список упорядочен по созданию пользователей.

`id` — положительное целое число. Значение `id` назначает сервер, в POST его передавать не нужно. После удаления идентификаторы не переиспользуются.

POST принимает только `Content-Type: application/json`:

```json
{
  "name": "Анна",
  "email": "anna@example.com"
}
```

Пример ответа с кодом `201 Created`:

```json
{
  "id": 1,
  "name": "Анна",
  "email": "anna@example.com"
}
```

Пробелы по краям имени и email удаляются. Имя должно содержать от 1 до 100 символов, email — до 254 символов и соответствовать простому формату `имя@домен.зона`. Email уникален без учёта регистра. Обязательны оба поля; неизвестные поля отклоняются.

Ошибки возвращаются в JSON, например:

```json
{"error":"Пользователь с таким email уже существует"}
```

| Код | Когда возвращается |
| --- | --- |
| 400 Bad Request | Некорректный JSON, отсутствующие/невалидные поля, ошибочные параметры |
| 404 Not Found | Пользователь или маршрут не найден |
| 405 Method Not Allowed | Метод не поддерживается для существующего маршрута |
| 409 Conflict | Email уже используется |
| 415 Unsupported Media Type | Тело запроса передано не в JSON |
| 500 Internal Server Error | Непредвиденная ошибка; детали записываются в журнал сервера |

## Демонстрация для преподавателя

После запуска сервера откройте второй PowerShell и выполните:

```powershell
# 1. Создание (201, id = 1 при первом запуске)
$body = @{ name = 'Анна'; email = 'anna@example.com' } | ConvertTo-Json
Invoke-WebRequest -Uri 'http://localhost:8080/users' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing

# 2. GET со всеми query-параметрами (200)
$name = [Uri]::EscapeDataString('Ан')
Invoke-WebRequest -Uri "http://localhost:8080/users?name=$name&limit=10&offset=0" -UseBasicParsing

# 3. GET с path-параметром (200)
Invoke-WebRequest -Uri 'http://localhost:8080/users/1' -UseBasicParsing

# 4. Удаление (204, пустое тело)
Invoke-WebRequest -Uri 'http://localhost:8080/users/1' -Method Delete -UseBasicParsing
```

Готовые запросы для IntelliJ IDEA HTTP Client и совместимых REST-клиентов находятся в **`requests.http`**. Там также есть запросы для проверки ошибок 400, 404 и 409. Если HTTP Client недоступен в вашей редакции IDEA, используйте PowerShell или Postman. В Windows PowerShell ответы 4xx вызывают исключение — это ожидаемо; HTTP Client/Postman удобнее для просмотра таких ответов.

## Проверки

```powershell
.\mvnw.cmd test
```

Интеграционные тесты используют Ktor `testApplication`, не требуют запуска отдельного сервера и базы. Проверяют JSON, полный цикл создания/получения/удаления, фильтрацию и пагинацию, повторный email, параметры, отсутствующие записи, неверные поля и JSON, неверный Content-Type и HTTP-метод. При загрузке на GitHub сборка также запускается через `.github/workflows/build.yml`.

## Файлы проекта

```text
src/main/kotlin/
  Main.kt             — запуск Netty, настройка порта
  Application.kt      — плагины Ktor и обработка ошибок
  User.kt             — сериализуемые модели
  UserRepository.kt   — потокобезопасное хранилище в памяти
  UserRoutes.kt       — HTTP-маршруты и проверка входных данных
src/main/resources/logback.xml
src/test/kotlin/ApplicationTest.kt
requests.http          — примеры HTTP-запросов
pom.xml                — зависимости и сборка исполняемого JAR
mvnw / mvnw.cmd        — Maven Wrapper
```

## Публикация в GitHub

Проект нужно опубликовать самостоятельно. Файлы сборки, IDE и локальный кэш исключены через `.gitignore`.

1. На GitHub создайте **публичный (Public)** пустой репозиторий `KotlinPostgres`. Не добавляйте README, `.gitignore` и лицензию при создании, чтобы репозиторий был пустым.
2. В терминале в папке этого проекта выполните (замените `YOUR_LOGIN` на свой логин):

```powershell
git remote add origin https://github.com/YOUR_LOGIN/KotlinPostgres.git
git push -u origin main
```

Если в проекте ещё нет коммита, перед этими командами выполните:

```powershell
git init -b main
git add .
git commit -m "Implement Kotlin Ktor users API"
```

Альтернатива с GitHub CLI после входа `gh auth login` (она сама создаст публичный репозиторий и отправит коммит):

```powershell
gh repo create KotlinPostgres --public --source=. --remote=origin --push
```

3. Проверьте, что репозиторий открывается без входа в аккаунт и GitHub Actions проходит. Преподавателю передайте ссылку вида `https://github.com/YOUR_LOGIN/KotlinPostgres`.

## Документация

- [Ktor: ContentNegotiation и kotlinx.serialization](https://ktor.io/docs/server-serialization.html)
- [Ktor: обработка ошибок через StatusPages](https://ktor.io/docs/server-status-pages.html)
- [Ktor: тестирование приложения](https://ktor.io/docs/server-testing.html)
