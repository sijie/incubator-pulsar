/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.bookkeeper.mledger;

import com.google.common.annotations.Beta;

@SuppressWarnings("serial")
@Beta
public class ManagedLedgerException extends Exception {
    public ManagedLedgerException(String msg) {
        super(msg);
    }

    public ManagedLedgerException(Throwable e) {
        super(e);
    }

    public static ManagedLedgerException getManagedLedgerException(Throwable e) {
        if (e instanceof ManagedLedgerException) {
            return (ManagedLedgerException) e;
        }
        return new ManagedLedgerException(e);
    }
    
    public static class MetaStoreException extends ManagedLedgerException {
        public MetaStoreException(Exception e) {
            super(e);
        }
    }

    public static class BadVersionException extends MetaStoreException {
        public BadVersionException(Exception e) {
            super(e);
        }
    }

    public static class ManagedLedgerFencedException extends ManagedLedgerException {
        public ManagedLedgerFencedException() {
            super(new Exception("Attempted to use a fenced managed ledger"));
        }

        public ManagedLedgerFencedException(Exception e) {
            super(e);
        }
    }

    public static class ManagedLedgerTerminatedException extends ManagedLedgerException {
        public ManagedLedgerTerminatedException(String msg) {
            super(msg);
        }
    }

    public static class NoMoreEntriesToReadException extends ManagedLedgerException {
        public NoMoreEntriesToReadException(String msg) {
            super(msg);
        }
    }

    public static class InvalidCursorPositionException extends ManagedLedgerException {
        public InvalidCursorPositionException(String msg) {
            super(msg);
        }
    }

    public static class ConcurrentFindCursorPositionException extends ManagedLedgerException {
        public ConcurrentFindCursorPositionException(String msg) {
            super(msg);
        }
    }

    public static class CursorAlreadyClosedException extends ManagedLedgerException {
        public CursorAlreadyClosedException(String msg) {
            super(msg);
        }
    }

    public static class TooManyRequestsException extends ManagedLedgerException {
        public TooManyRequestsException(String msg) {
            super(msg);
        }
    }

    public static class NonRecoverableLedgerException extends ManagedLedgerException {
        public NonRecoverableLedgerException(String msg) {
            super(msg);
        }
    }

    public static class InvalidReplayPositionException extends ManagedLedgerException {
        public InvalidReplayPositionException(String msg) {
            super(msg);
        }
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // Disable stack traces to be filled in
        return null;
    }
}
