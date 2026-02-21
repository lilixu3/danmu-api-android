package com.example.danmuapiapp.domain.model

/**
 * 统一的应用错误类型
 */
sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class RootError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class CoreError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class FileError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class ServiceError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError(message, cause)

    fun toDisplayMessage(): String {
        return when (this) {
            is NetworkError -> "网络错误: $message"
            is RootError -> "Root 权限错误: $message"
            is CoreError -> "核心错误: $message"
            is FileError -> "文件错误: $message"
            is ServiceError -> "服务错误: $message"
            is UnknownError -> "未知错误: $message"
        }
    }
}

/**
 * 错误处理工具类
 */
object ErrorHandler {
    fun handle(error: Throwable): AppError {
        return when (error) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.io.FileNotFoundException -> AppError.FileError(
                message = error.message ?: "文件未找到",
                cause = error
            )
            is java.io.IOException -> AppError.NetworkError(
                message = error.message ?: "网络连接失败",
                cause = error
            )
            is SecurityException -> AppError.RootError(
                message = error.message ?: "权限不足",
                cause = error
            )
            else -> AppError.UnknownError(
                message = error.message ?: "发生未知错误",
                cause = error
            )
        }
    }

    fun buildDetailedMessage(error: Throwable): String {
        val builder = StringBuilder()
        builder.append("${error::class.java.simpleName}: ${error.message ?: "无消息"}")

        error.cause?.let { cause ->
            builder.append("\n原因: ${cause::class.java.simpleName}: ${cause.message ?: "无消息"}")
        }

        return builder.toString()
    }
}
