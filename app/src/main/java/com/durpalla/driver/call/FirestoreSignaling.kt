package com.durpalla.driver.call

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Signaling through calls/{callId}. The backend creates the doc on POST /calls (status ringing, caller/callee ids);
 * clients write offer/answer and their own candidate subcollection. Firestore rules (docs/firestore.rules) allow
 * only callerId/calleeId, so the client signs in with the backend-issued Firebase custom token (uid = Durpalla userId).
 */
class FirestoreSignaling(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val customToken: suspend () -> String?,
) {
    private fun doc(callId: String) = db.collection(CallDoc.COLLECTION).document(callId)

    suspend fun ensureSignedIn() {
        if (auth.currentUser != null) return
        val t = customToken() ?: throw CallException(CallError.UNAUTHORIZED, "no firebase token")
        auth.signInWithCustomToken(t).await()
    }

    suspend fun read(callId: String): CallDoc? = doc(callId).get().await().data?.let { CallDoc.from(it, ::millis) }

    suspend fun writeOffer(callId: String, offer: Sdp) { doc(callId).update("offer", offer.toMap()).await() }
    suspend fun writeAnswer(callId: String, answer: Sdp) { doc(callId).update(mapOf("answer" to answer.toMap(), "answeredAt" to FieldValue.serverTimestamp())).await() }

    /** Client-side fallback; the accept/decline/end endpoints are authoritative and also write status. */
    suspend fun markEnded(callId: String, status: CallStatus, endedBy: String, reason: String) = runCatching {
        doc(callId).update(mapOf("status" to status.wire, "endedAt" to FieldValue.serverTimestamp(), "endedBy" to endedBy, "reason" to reason)).await()
    }

    fun addCandidate(callId: String, asCaller: Boolean, c: IceCandidateDto) {
        doc(callId).collection(if (asCaller) CallDoc.CALLER_CANDIDATES else CallDoc.CALLEE_CANDIDATES).add(c.toMap())
    }

    fun listen(callId: String, onDoc: (CallDoc) -> Unit): ListenerRegistration =
        doc(callId).addSnapshotListener { s, _ -> s?.data?.let { onDoc(CallDoc.from(it, ::millis)) } }

    /** Remote candidates: the caller reads calleeCandidates and vice versa. */
    fun listenCandidates(callId: String, asCaller: Boolean, onCandidate: (IceCandidateDto) -> Unit): ListenerRegistration =
        doc(callId).collection(if (asCaller) CallDoc.CALLEE_CANDIDATES else CallDoc.CALLER_CANDIDATES).addSnapshotListener { s, _ ->
            s?.documentChanges?.filter { it.type == DocumentChange.Type.ADDED }?.forEach { ch -> IceCandidateDto.from(ch.document.data)?.let(onCandidate) }
        }

    companion object {
        fun millis(v: Any?): Long? = when (v) { is Timestamp -> v.toDate().time; is java.util.Date -> v.time; is Number -> v.toLong(); else -> null }
    }
}
