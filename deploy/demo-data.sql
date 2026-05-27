-- Demo reset for Meventur.
-- Run in DBeaver or psql against the meventus database.
-- It removes event-related test data and loads presentation events.
-- Real Telegram users are kept, but demo users below are upserted.

BEGIN;

DELETE FROM app_state WHERE "key" LIKE 'reminder.%';
TRUNCATE TABLE custom_reminders, participants, event_tags, events RESTART IDENTITY CASCADE;

INSERT INTO users (telegram_id, username, first_name, created_at) VALUES
  (900000000001, 'demo_organizer', 'Анна', now()),
  (900000000002, 'demo_alex', 'Алексей', now()),
  (900000000003, 'demo_maria', 'Мария', now()),
  (900000000004, 'demo_ivan', 'Иван', now()),
  (900000000005, 'demo_team', 'Команда', now())
ON CONFLICT (telegram_id) DO UPDATE SET
  username = EXCLUDED.username,
  first_name = EXCLUDED.first_name;

CREATE TEMP TABLE demo_event_ids (
  slug text PRIMARY KEY,
  id bigint NOT NULL
) ON COMMIT DROP;

WITH inserted AS (
  INSERT INTO events (
    owner_id,
    title,
    short_description,
    description,
    photo_file_id,
    address,
    starts_at,
    cost,
    status,
    created_at,
    payment_type,
    sbp_phone,
    sbp_name,
    visibility,
    registration_mode,
    group_chat_id
  ) VALUES
    (
      900000000001,
      'Демо-день проектов',
      'Короткие питчи, обратная связь и нетворкинг.',
      'Команды показывают прототипы, получают вопросы от жюри и договариваются о следующих шагах.',
      NULL,
      'Точка кипения, зал 2',
      now() + interval '2 days' + interval '3 hours',
      0,
      'PUBLISHED',
      now(),
      'ON_SITE',
      NULL,
      NULL,
      'PUBLIC',
      'FREE',
      NULL
    ),
    (
      900000000001,
      'Закрытая планёрка команды',
      'Приватная встреча для синхронизации задач.',
      'Разбираем прогресс, риски, роли и план до финальной презентации.',
      NULL,
      'Переговорная 4',
      now() + interval '1 day' + interval '1 hour',
      0,
      'PUBLISHED',
      now(),
      'ON_SITE',
      NULL,
      NULL,
      'PRIVATE',
      'INVITE_ONLY',
      NULL
    ),
    (
      900000000001,
      'Воркшоп по питчу',
      'Практика выступления и упаковки идеи.',
      'За 90 минут собираем структуру выступления, тренируем тайминг и формулируем сильный финал.',
      NULL,
      'Лекторий, 3 этаж',
      now() + interval '4 days' + interval '2 hours',
      500,
      'PUBLISHED',
      now(),
      'ADVANCE',
      '+79991234567',
      'Анна Петрова',
      'PUBLIC',
      'FREE',
      NULL
    ),
    (
      900000000001,
      'Командный созвон перед финалом',
      'Групповое событие со свободной записью.',
      'Проверяем демо, распределяем доклад и фиксируем список задач перед финалом.',
      NULL,
      'Онлайн',
      now() + interval '6 hours',
      0,
      'PUBLISHED',
      now(),
      'ON_SITE',
      NULL,
      NULL,
      'PRIVATE',
      'FREE',
      -1009000000001
    )
  RETURNING id, title
)
INSERT INTO demo_event_ids (slug, id)
SELECT
  CASE title
    WHEN 'Демо-день проектов' THEN 'demo_day'
    WHEN 'Закрытая планёрка команды' THEN 'team_private'
    WHEN 'Воркшоп по питчу' THEN 'pitch_workshop'
    WHEN 'Командный созвон перед финалом' THEN 'group_call'
  END,
  id
FROM inserted;

INSERT INTO event_tags (event_id, tag)
SELECT id, tag
FROM demo_event_ids
CROSS JOIN LATERAL (
  VALUES
    ('demo_day', 'IT'),
    ('demo_day', 'INDOORS'),
    ('team_private', 'INDOORS'),
    ('pitch_workshop', 'IT'),
    ('pitch_workshop', 'INDOORS'),
    ('group_call', 'IT')
) AS tags(slug, tag)
WHERE demo_event_ids.slug = tags.slug;

INSERT INTO participants (event_id, user_id, joined_at, contributed, payment_status, payer_phone, payer_name)
SELECT e.id, p.user_id, now(), p.contributed, p.payment_status, p.payer_phone, p.payer_name
FROM demo_event_ids e
JOIN (
  VALUES
    ('demo_day', 900000000002, 0, 'NOT_REQUIRED', NULL, NULL),
    ('demo_day', 900000000003, 0, 'NOT_REQUIRED', NULL, NULL),
    ('pitch_workshop', 900000000002, 500, 'CONFIRMED', '+79990000002', 'Алексей'),
    ('pitch_workshop', 900000000004, 0, 'PENDING', '+79990000004', 'Иван'),
    ('group_call', 900000000002, 0, 'NOT_REQUIRED', NULL, NULL),
    ('group_call', 900000000003, 0, 'NOT_REQUIRED', NULL, NULL),
    ('group_call', 900000000005, 0, 'NOT_REQUIRED', NULL, NULL)
) AS p(slug, user_id, contributed, payment_status, payer_phone, payer_name)
  ON e.slug = p.slug;

INSERT INTO custom_reminders (event_id, seconds_before, message, sent, created_at)
SELECT id, seconds_before, message, false, now()
FROM demo_event_ids
JOIN (
  VALUES
    ('demo_day', 3600, 'Через час начинаем демо-день. Проверьте презентацию и ссылку на прототип.'),
    ('pitch_workshop', 1800, 'Через 30 минут стартует воркшоп. Подготовьте черновик питча.'),
    ('group_call', 300, 'Через 5 минут созвон команды. Подключайтесь без опозданий.')
) AS reminders(slug, seconds_before, message)
  ON demo_event_ids.slug = reminders.slug;

COMMIT;
