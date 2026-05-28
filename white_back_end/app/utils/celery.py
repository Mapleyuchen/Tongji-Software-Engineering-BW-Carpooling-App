from celery import Celery
from flask import current_app


def send_celery_task(task_name, args=None, kwargs=None, eta=None, countdown=None):
    celery = Celery(
        current_app.import_name,
        broker=current_app.config["CELERY_BROKER_URL"],
        backend=current_app.config["CELERY_RESULT_BACKEND"],
    )
    celery.conf.update(
        timezone="Asia/Shanghai",
        enable_utc=False,
    )
    return celery.send_task(
        task_name,
        args=args or [],
        kwargs=kwargs or {},
        eta=eta,
        countdown=countdown,
    )
