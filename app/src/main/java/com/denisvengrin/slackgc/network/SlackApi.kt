package com.denisvengrin.slackgc.network

import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.FilesResponse
import com.google.gson.JsonObject
import io.reactivex.Flowable
import io.reactivex.Single
import retrofit2.Call
import retrofit2.http.*

interface SlackApi {

    @FormUrlEncoded
    @POST("api/oauth.access")
    fun login(@FieldMap fields: Map<String, String?>): Single<AuthResponse>

    @GET("api/files.list")
    fun getFiles(@QueryMap query: Map<String, String?>): Single<FilesResponse>

    @POST("api/files.delete")
    fun deleteFile(@Body body: JsonObject = JsonObject(),
                   @QueryMap query: Map<String, String?>): Call<FilesResponse>

    companion object {
        const val BASE_URL = "https://slack.com"
    }
}