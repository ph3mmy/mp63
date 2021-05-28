package ng.com.plustech.mp63kotlin.models

data class TransactionResponse(var responseCode: String, var message: String, var responseAmount: String, var terminalId: String, var rrn: String, var transmissionDateTime: String) {

}
