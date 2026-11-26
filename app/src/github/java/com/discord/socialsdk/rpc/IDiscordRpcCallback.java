package com.discord.socialsdk.rpc;

public interface IDiscordRpcCallback extends android.os.IInterface {
    String DESCRIPTOR = "com.discord.socialsdk.rpc.IDiscordRpcCallback";

    void onFrame(String frame) throws android.os.RemoteException;
    void onClose(int code, String message) throws android.os.RemoteException;

    abstract class Stub extends android.os.Binder implements IDiscordRpcCallback {
        static final int TRANSACTION_onFrame = android.os.IBinder.FIRST_CALL_TRANSACTION;
        static final int TRANSACTION_onClose = android.os.IBinder.FIRST_CALL_TRANSACTION + 1;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IDiscordRpcCallback asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IDiscordRpcCallback) return (IDiscordRpcCallback) iin;
            return new Proxy(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
            throws android.os.RemoteException {
            if (code == TRANSACTION_onFrame) {
                data.enforceInterface(DESCRIPTOR);
                onFrame(data.readString());
                reply.writeNoException();
                return true;
            } else if (code == TRANSACTION_onClose) {
                data.enforceInterface(DESCRIPTOR);
                int c = data.readInt();
                String msg = data.readString();
                onClose(c, msg);
                reply.writeNoException();
                return true;
            } else if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IDiscordRpcCallback {
            private final android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return mRemote;
            }

            @Override
            public void onFrame(String frame) throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(frame);
                    mRemote.transact(TRANSACTION_onFrame, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void onClose(int code, String message) throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(code);
                    data.writeString(message);
                    mRemote.transact(TRANSACTION_onClose, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
