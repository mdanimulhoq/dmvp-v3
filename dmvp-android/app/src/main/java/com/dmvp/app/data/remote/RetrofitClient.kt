package com.dmvp.app.data.remote
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
object RetrofitClient {
    private var retrofit: Retrofit? = null
    fun init() {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl("https://dmvp-v3.onrender.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }
}
