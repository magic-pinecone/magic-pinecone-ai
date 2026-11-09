package org.mpc.core

/** The failure a stored-preferences write raises: DataStore's IOException on Android and iOS. */
expect open class StorageIOException : Exception
