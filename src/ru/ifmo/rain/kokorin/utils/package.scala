package ru.ifmo.rain.kokorin

package object utils {
    def withResources[T <: AutoCloseable, R](resource: T)(f: T => R): R = {
        try {
            val result = f(resource)
            resource.close()
            result
        } catch {
            case e: Throwable => {
                try {
                    resource.close()
                } catch {
                    case ee: Throwable => e.addSuppressed(ee)
                }
                throw e
            }
        }
    }
}
