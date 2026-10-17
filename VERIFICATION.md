# Фактическая проверка Assignment 4 — вариант 10

Проверено: 2026-10-17 22:31 UTC+05:00. Источники: оригинальное задание DOCX и handoff с Desktop.
При расхождениях использовано оригинальное задание и текущий запрос пользователя.

## Сборка и тесты

JDK 25.0.1, Maven 3.9.11, SQLite JDBC 3.50.3.0, JUnit 5.11.4, JaCoCo 0.8.14.
Фактически выполнено `mvn -B -Dmaven.repo.local=USER_HOME\Documents\Codex\maven-cache clean verify`.
Нестандартный путь кеша нужен только для разрешений среды Codex; обычная команда — `mvn clean verify`.

**BUILD SUCCESS. Тестов: 72; failures: 0; errors: 0; skipped: 0.**

Проверены успешная A → B → C, сохранённые компенсации после отказов B/C, порядок B → A,
повторное открытие файла после A/B и между отменами, отсутствие повторных отмен,
корректные/запрещённые переходы, дополнительный отказ неверного ввода и занятого места,
самостоятельная работа подсистем, CLI и отсутствие перезаписи самостоятельных записей.
SQL-trigger RAISE(ABORT) проверяет откат результата, журнала и прогресса вместе для
A, B, успешного C, отказа C, отмены B и отмены A. Ошибка SQL при проверке места остаётся
повторяемой, а не превращается в завершённую бизнес-компенсацию.

Хранилища тестов — отдельные файлы SQLite в `@TempDir`; зависимости от порядка нет.
Domain-specific helper: `concertgoerBuysSeat(...)` в WorkflowTest.

## Line coverage

| Подсистема / фасад | Выполненные строки | Покрытие |
|---|---:|---:|
| concert/reservation | 70/70 | 100.00% |
| concert/processing | 64/64 | 100.00% |
| concert/access | 84/85 | 98.82% |
| TicketPurchasingFacade | 125/134 | 93.28% |
| TicketPurchasingAdminFacade | 59/59 | 100.00% |
| Все обязательные области вместе | 402/412 | 97.57% |
| Весь основной код, включая CLI и инфраструктуру | 541/563 | 96.09% |

Maven `verify` запрещает результат ниже 80% отдельно для каждой подсистемы и каждого фасада.
Полный HTML-отчёт: [docs/coverage/index.html](docs/coverage/index.html).
XML и CSV находятся там же; свежие отчёты создаются в `target/site/jacoco`.
Результаты JUnit и лог сборки: [docs/verification](docs/verification).

## Настоящий CLI и SQLite

Проверка запускает готовый JAR отдельными JVM и независимо читает SQLite через Python sqlite3.
Успешно пройдены шесть сценариев: покупка, повторный ticketCode, закрытый вход,
перезапуск после A, неверный ввод, перезапуск незавершённой компенсации.
Всего 14 запусков JVM. Проверены промежуточные COMPENSATING_B / COMPENSATING_A,
финальные состояния и отсутствие повторных B_VOIDED / A_RELEASED в сохранённом журнале.
Транскрипты, SQL-снимки и JSON-сводка: [docs/verification/cli](docs/verification/cli).

## Минимальная архитектура и файлы

- A: `concert/reservation/SeatReservation`, `SeatingChart`, `DynamicPricingEngine`, `ReservationRepository`.
- B: `concert/processing/TicketProcessing`, `FraudChecker`, `TicketPrinter`, `TicketProcessingRepository`.
- C: `concert/access/TicketAccess`, `AccessControlGate`, `RefundService`, `TicketAccessRepository`.
- Координация: `TicketPurchasingFacade`, `TicketPurchasingAdminFacade`, `Workflow`, `WorkflowRepository`.
- Инфраструктура/клиент: `Database`, `BookingException`, `Main`; запуск: `run.ps1`.
- Изменены `pom.xml`, `.gitignore`, исходный `SeatReservationTest` (добавлен import).
- Исходный SeatReservation перенесён из соседнего TicketConcert: сохранены конструкторы,
  имена полей, getStatus/reserve/release; добавлены package, final и валидация.
- Добавлены тесты в `src/test/java/concert`, README и `docs/classes.svg`.

В каждой подсистеме ровно четыре основных класса; чужие подсистемы не импортируются.
Один файл SQLite, каждый репозиторий работает только со своими таблицами.
Фасад фиксирует этап + журнал + Workflow короткой транзакцией; общей транзакции A → B → C нет.
RefundService сохраняет локальный учебный кредит в KZT — реальные деньги не переводятся.

## Что не сделано автоматически

Исходники и тесты публикуются вместе в [https://github.com/Temirlan222/ConcertTicketing](https://github.com/Temirlan222/ConcertTicketing) реальными коммитами текущей датой.
История разработки задним числом не создаётся. Эти коммиты не доказывают, когда были
написаны отдельные тесты относительно реализации; требование Timely этим не подтверждено.
Ссылку в Moodle пользователь отправляет самостоятельно.
Работающую защиту и live coding пользователь выполняет сам.

## Три первых участка для защиты

1. `TicketPurchasingFacade.advanceOne`, `print`, `finalizeAccess`, `voidTicket`, `release`:
   границы транзакций, отказ B/C и порядок B → A.
2. `Workflow` и `WorkflowRepository`: сохранённое направление продолжения/отмены;
   `WorkflowTest.restartDuringCompensationDoesNotRepeatSavedVoid` доказывает восстановление.
3. `SeatReservation` + `ReservationRepository`: сущность сама запрещает переходы,
   сохранение/загрузка независимы от других подсистем; такой же принцип применяется B/C.
