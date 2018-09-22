package com.example.contract

import com.example.state.IOUState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOU].
 *
 * For a new [IOU] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOU].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class IOUContract : Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "com.example.contract.IOUContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.Create -> verifyCreate(tx)
            is Commands.Pay -> verifyPay(tx)
            else -> throw IllegalStateException("Comando [${command.value::class.java.canonicalName}] sem validação.")
        }
        requireThat {
            "All of the participants must be signers." using (
                    command.signers.containsAll(
                        tx.outputsOfType<IOUState>().flatMap {
                            it.participants.map { it.owningKey }
                        }
                    ))
        }
    }

    fun verifyCreate(tx: LedgerTransaction) {
        requireThat {
            // Generic constraints around the IOU transaction.
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<IOUState>().single()
            "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)

            // IOU-specific constraints.
            "The IOU's value must be non-negative." using (out.value > 0)
            "O valor de pagamento deve estar zerado." using (out.payedValue == 0)
        }
    }

    fun verifyPay(tx: LedgerTransaction) {
        requireThat {
            "Only one input state should be payed." using (tx.inputs.size == 1)
            "Only one output state should be payed." using (tx.outputs.size == 1)
            val input = tx.inputsOfType<IOUState>().single()
            val output = tx.outputsOfType<IOUState>().single()

            "O input e o output devem possuir o mesmo ID" using (input.linearId == output.linearId)

           /* "Os inputs e outputs devem possuir o mesmo ID" using  tx.groupStates<IOUState, UniqueIdentifier>()
            {
                it.linearId
            }.all {
                it.inputs.size == it.outputs.size &&
                        it.inputs.size == 1
            }*/

            "O valor pago não deve ser maior que a divida." using (
                    output.payedValue <=  input.value)
            "Deve ter tido um pagamento." using (
                    output.payedValue > input.payedValue)

            "A unica informação que pode ser alterada é o valor pago." using (output.naoAlterouValoresPagamento(input)                   )
        }

    }

    fun IOUState.naoAlterouValoresPagamento(other: IOUState): Boolean =
            this.lender == other.lender &&
                    this.borrower == other.borrower &&
                    this.value == other.value
    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
        class Pay : Commands
    }
}
