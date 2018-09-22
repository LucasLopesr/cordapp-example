package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.IOUContract
import com.example.state.IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

object PayIOUFlow {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val valorPagamento: Int,
                    val idIOUState: UUID) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            // buscar os inputs

            val vaultIOUState = serviceHub.vaultService.queryBy<IOUState>(
                    QueryCriteria.LinearStateQueryCriteria(
                            uuid = listOf(idIOUState))).states.single() // current state
            // definir o notary
            val notary = vaultIOUState.state.notary

            // construir a transação

                // construir os outputs
            val iouState = vaultIOUState.state.data

            requireThat {
                "Eu devo ser o borrower." using(iouState.borrower == ourIdentity)
            }

            val outputIOUState = iouState.copy(payedValue = iouState.payedValue + valorPagamento)

                // construir o comando
            val comando = Command(IOUContract.Commands.Pay(),
                    iouState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addCommand(comando)
                    .addInputState(vaultIOUState)
                    .addOutputState(outputIOUState,
                            IOUContract::class.java.canonicalName)

            // verificar a transação

            txBuilder.verify(serviceHub)

            // assinar a transação

            val txAssinadaPorMim = serviceHub.signInitialTransaction(txBuilder)


            // coletar assinaturas | CollectSignaturesFlow
            val session = initiateFlow(iouState.lender)
            val txAssinadaPorTodos = subFlow(
                    CollectSignaturesFlow(txAssinadaPorMim, listOf(session)))

            // notorizar e gravar na base | FinalityFlow
            return subFlow(FinalityFlow(txAssinadaPorTodos))
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherParty: FlowSession) : FlowLogic<SignedTransaction>(){

        @Suspendable
        override fun call(): SignedTransaction {
        // receber um objeto, abrir os dados verificar se está tudo certo, assinar, enviar de volta

            val signFlow = object : SignTransactionFlow(otherParty){
                override fun checkTransaction(stx: SignedTransaction) {
                    // fazer validações especificas
                    // importante checar que sou o lender
                   requireThat {
                       "Eu devo ser o Lender" using(stx.coreTransaction.outputsOfType<IOUState>().all {
                           it.lender == ourIdentity
                       })

                   }

                }
            }

            return subFlow(signFlow)
        }
    }

}