# Scala Jobcoin

The Jobcoin mixer takes a sender address and specified amount of Jobcoin to send,
as well as a list of addresses and corresponding amounts of Jobcoin to send to.

Instead of immediately sending the Jobcoin to each of the addresses, the mixer first
produces a `DepositAddress` that the sender can deposit all Jobcoin in.

Next, the `DepositAddress` moves the Jobcoin to a `HouseAddress` which mixes all the
Jobcoin together. On a periodic task, the `HouseAddress` then moves the Jobcoin to each
of the recipient addresses that were initially specified, and the corresponding amounts for
them as well.

### Run
`sbt run`

Here is an example of what to specify at the command line once the application runs:
```$xslt
James,20.0,Alice,5.0,Bob,15.0
```
This means that `James` will send 20.0 Jobcoin to `Alice` and `Bob`, who will receive
5.0 and 15.0 Jobcoin, respectively.

Here, given the addresses `Alice` and `Bob`, the mixer will compute a `DepositAddress`
for `James` to send to. `James` will then send the 20.0 to the `DepositAddress`, and then
the `DepositAddress` will move those pooled Jobcoin to the `HouseAddress`. The `HouseAddress`
will be responsible for sending out the 20.0 Jobcoin to `Alice` and `Bob`, based on their specified
amounts of 5.0 and 15.0.

Here is example output from running the program against my API for `Alice`, `Bob`, and `James`:
```$xslt
Enter comma-separated list of form: <SenderAddress>, <SenderAmount>, <RecipientAddress1>, <RecipientAmount1>, <RecipientAddress2>, <RecipientAmount2>
James,10.0,Alice,6.0,Bob,4.0
James can now send Jobcoin to deposit address [DepositAddress3]
[James] now sending mixed Jobcoin amount of [10.0] to [DepositAddress3]
[James] successfully transferred [10.0] Jobcoin to [DepositAddress3]
[DepositAddress3] received a new DepositAddressTransfer
[DepositAddress3] transfering [6.0] to pooled [HouseAddress]
[DepositAddress3] successfully transferred [6.0] to pooled [HouseAddress]
[HouseAddress] received an incoming transfer from [DepositAddress3]
[HouseAddress] creating Jobcoin amount for [Alice] with value [6.0]
[DepositAddress3] transfering [4.0] to pooled [HouseAddress]
[DepositAddress3] successfully transferred [4.0] to pooled [HouseAddress]
[HouseAddress] received an incoming transfer from [DepositAddress3]
[HouseAddress] creating Jobcoin amount for [Bob] with value [4.0]
[HouseAddress] sending [4.0] Jobcoin to recipient address [Bob]
[HouseAddress] sending [6.0] Jobcoin to recipient address [Alice]
[HouseAddress] removing information for [Alice]
[HouseAddress] removing information for [Bob]
Bob,3.5,James,2.0,Alice,1.5
Bob can now send Jobcoin to deposit address [DepositAddress3]
[Bob] now sending mixed Jobcoin amount of [3.5] to [DepositAddress3]
[Bob] successfully transferred [3.5] Jobcoin to [DepositAddress3]
[DepositAddress3] received a new DepositAddressTransfer
[DepositAddress3] transfering [2.0] to pooled [HouseAddress]
[DepositAddress3] successfully transferred [2.0] to pooled [HouseAddress]
[HouseAddress] received an incoming transfer from [DepositAddress3]
[HouseAddress] creating Jobcoin amount for [James] with value [2.0]
[DepositAddress3] transfering [1.5] to pooled [HouseAddress]
[DepositAddress3] successfully transferred [1.5] to pooled [HouseAddress]
[HouseAddress] received an incoming transfer from [DepositAddress3]
[HouseAddress] creating Jobcoin amount for [Alice] with value [1.5]
[HouseAddress] sending [2.0] Jobcoin to recipient address [James]
[HouseAddress] sending [1.5] Jobcoin to recipient address [Alice]
[HouseAddress] removing information for [James]
[HouseAddress] removing information for [Alice]
```

### Test

There are feature tests implemented for the `JobcoinClient` and `TransactionRequest` classes,
which make API calls.

`sbt test`
