package com.lenovo.omnidemo.traditional.net

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming


/**
 * @date 2025/5/13 17:25
 * @author zhk
 */
interface ApiService {

    @Multipart
    @POST("generate")
    suspend fun generate(@Part("text") text: RequestBody,
                       @Part("image") image: RequestBody?,
                       @Part("audio") audio: RequestBody?,
                       @Part("video") video: RequestBody?
    ): Response<UploadResponse>

    @Multipart
    @POST("generate")
    suspend fun generateWithUpload(@Part("text") text: RequestBody?,
                                   @Part image: MultipartBody.Part?,
                                   @Part audio: MultipartBody.Part?,
                                   @Part video: MultipartBody.Part?
    ): Response<UploadResponse>

    @GET("download")
    suspend fun download(): ResponseBody

    @Streaming
    @Multipart
    @POST("process_form_stream")
    fun processFormStream(@Part("text") text: RequestBody?,
                                  @Part image: MultipartBody.Part?,
                                  @Part audio: MultipartBody.Part?,
                                  @Part video: MultipartBody.Part?
    ): Call<ResponseBody>

}