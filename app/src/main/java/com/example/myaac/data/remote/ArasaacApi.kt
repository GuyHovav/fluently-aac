package com.example.myaac.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface ArasaacApi {
    @GET("pictograms/{locale}/search/{query}")
    suspend fun searchPictograms(
        @Path("locale") locale: String,
        @Path("query") query: String
    ): List<ArasaacPictogram>
}
