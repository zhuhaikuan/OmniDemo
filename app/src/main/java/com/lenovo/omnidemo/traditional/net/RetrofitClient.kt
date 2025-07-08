package com.lenovo.omnidemo.traditional.net

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


/**
 * @date 2025/5/13 16:47
 * @author zhk
 */
object RetrofitClient {
//    private const val BASE_URL = "http://10.183.171.42:33445"
//    private const val BASE_URL = "http://10.110.129.129:10000/"
    private const val BASE_URL = "http://10.110.158.191:30380/"

    private val loggingInterceptor = HttpLoggingInterceptor { message -> println("OkHttp: $message") }.apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient  = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun <T> createService(service: Class<T>): T {
        return retrofit.create(service)
    }


}