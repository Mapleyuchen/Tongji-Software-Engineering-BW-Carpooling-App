from app import create_app
from app.extensions import socketio

app = create_app()


if __name__ == "__main__":
    socketio.run(
        app,
        debug=True,
        port=8443,
        use_reloader=False,
        allow_unsafe_werkzeug=True,
    )
