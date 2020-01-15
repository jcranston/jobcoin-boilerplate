case class Recipient(name: String, balance: Double) {
  def updateBalance(newBalance: Double) = this.copy(balance = newBalance)
}
