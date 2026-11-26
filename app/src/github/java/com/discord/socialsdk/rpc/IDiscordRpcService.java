package com.discord.socialsdk.rpc;

public interface IDiscordRpcService extends android.os.IInterface {
    String DESCRIPTOR = "com.discord.socialsdk.rpc.IDiscordRpcService";

    IDiscordRpcConnection connect(long applicationId, String version, IDiscordRpcCallback callback)
        throws android.os.RemoteException;

    abstract class Stub extends android.os.Binder implements IDiscordRpcService {
        static final int TRANSACTION_connect = android.os.IBinder.FIRST_CALL_TRANSACTION;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IDiscordRpcService asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IDiscordRpcService) return (IDiscordRpcService) iin;
            return new Proxy(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
            throws android.os.RemoteException {
            if (code == TRANSACTION_connect) {
                data.enforceInterface(DESCRIPTOR);
                long arg0 = data.readLong();
                String arg1 = data.readString();
                IDiscordRpcCallback arg2 = IDiscordRpcCallback.Stub.asInterface(data.readStrongBinder());
                IDiscordRpcConnection result = connect(arg0, arg1, arg2);
                reply.writeNoException();
                reply.writeStrongBinder(result != null ? result.asBinder() : null);
                return true;
            } else if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IDiscordRpcService {
            private final android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return mRemote;
            }

            @Override
            public IDiscordRpcConnection connect(long applicationId, String version, IDiscordRpcCallback callback)
                throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                IDiscordRpcConnection result;
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeLong(applicationId);
                    data.writeString(version);
                    data.writeStrongBinder(callback != null ? callback.asBinder() : null);
                    mRemote.transact(TRANSACTION_connect, data, reply, 0);
                    reply.readException();
                    result = IDiscordRpcConnection.Stub.asInterface(reply.readStrongBinder());
                } finally {
                    reply.recycle();
                    data.recycle();
                }
                return result;
            }
        }
    }
}
