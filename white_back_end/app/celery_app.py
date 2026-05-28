from celery import Celery

from app import create_app


flask_app = create_app()

celery = Celery(
    flask_app.import_name,
    broker=flask_app.config["CELERY_BROKER_URL"],
    backend=flask_app.config["CELERY_RESULT_BACKEND"],
)
celery.conf.update(
    broker_url=flask_app.config["CELERY_BROKER_URL"],
    result_backend=flask_app.config["CELERY_RESULT_BACKEND"],
    timezone="Asia/Shanghai",
    enable_utc=False,
    beat_schedule={
        "chat-scan-due-conversations": {
            "task": "chat.scan_due_conversations",
            "schedule": flask_app.config["CHAT_CLOSE_SWEEP_SECONDS"],
        },
    },
)


class FlaskContextTask(celery.Task):
    def __call__(self, *args, **kwargs):
        with flask_app.app_context():
            return self.run(*args, **kwargs)


celery.Task = FlaskContextTask

from app.tasks import chat  # noqa: F401,E402
