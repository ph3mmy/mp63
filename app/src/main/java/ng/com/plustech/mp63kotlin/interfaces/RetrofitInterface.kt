package ng.com.plustech.mp63kotlin.interfaces

import ng.com.plustech.mp63kotlin.models.*
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface RetrofitInterface {
    @POST("/api/v1/masterkeydownload")
    suspend fun getMasterKey(@Body terminal: Terminal): MasterKey

    @POST("/api/v1/sessionkeydownload")
    suspend fun getSessionKey(@Body terminal: Terminal): SessionKey

    @POST("/api/v1/pinkeydownload")
    suspend fun getPinKey(@Body terminal: Terminal): PinKey

    @POST("/api/v1/parameterdownload")
    suspend fun getParameter(@Body parameter: Parameter): ParameterResponse

    @POST
    suspend fun sendTransactionData(@Url url: String, @Body card: Card): TransactionResponse

}