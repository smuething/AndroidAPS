package info.nightscout.androidaps.plugins.general.nsclient2

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.UpdateGlucoseValueTransaction
import info.nightscout.androidaps.database.transactions.UpdateTemporaryTargetTransaction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.networking.nightscout.NightscoutServiceWrapper
import info.nightscout.androidaps.networking.nightscout.data.NightscoutCollection
import info.nightscout.androidaps.networking.nightscout.exceptions.BadInputDataException
import info.nightscout.androidaps.networking.nightscout.requests.EntryResponseBody
import info.nightscout.androidaps.networking.nightscout.responses.ResponseCode
import info.nightscout.androidaps.networking.nightscout.responses.id
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.source.NSClientSourcePlugin
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.PreferenceString
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy

/**
 * Created by adrian on 18.04.20.
 *
 * This is an interim class that holds legacy code that needs to be transformed and braught to NSClient2
 * Don't use this for production.
 *
 * TODO 1: migrate all uploads to NSClient2
 * TODO 2: make NSClient2 final again (and change protected-modifiers to private)
 *
 */

class NSClient2LegacyCodeholder constructor(
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    injector: HasAndroidInjector,
    context: Context,
    rxBus: RxBusWrapper,
    sp: SP,
    receiverStatusStore: ReceiverStatusStore,
    nightscoutServiceWrapper: NightscoutServiceWrapper,
    fabricPrivacy: FabricPrivacy,
    aapsSchedulers: AapsSchedulers,
    repository: AppRepository,
    nsClientSourcePlugin: NSClientSourcePlugin
) : NSClient2Plugin(aapsLogger, resourceHelper, injector, context, rxBus, sp, receiverStatusStore, nightscoutServiceWrapper, fabricPrivacy, aapsSchedulers, repository, nsClientSourcePlugin
) {

    fun legacyDoSync() {

        val cgmSync = PreferenceString(R.string.key_ns_cgm, "PUSH", sp, resourceHelper, rxBus)
        val ttSync = PreferenceString(R.string.key_ns_temptargets, "PUSH", sp, resourceHelper, rxBus)

        if (cgmSync.upload) {
            // CGM upload
            val list = repository
                .getModifiedBgReadingsDataAfterId(lastProcessedId.getValue(NightscoutCollection.ENTRIES).get())
                .doOnSuccess {
                    addToLog(EventNSClientNewLog("entries SYNC:", "${it.size} records", EventNSClientNewLog.Direction.OUT))
                }
                .flatMapObservable { Observable.fromIterable(it) }
                .flatMapSingle { gv ->
                    repository.getBgReadingsCorrespondingLastHistoryRecordSingle(gv.id)
                        .map { Pair(gv, it) }
                }
                .flatMapSingle {
                    Single.fromCallable { uploadGlucoseValue(it) }
                }
                .toList()
        }

        if (ttSync.upload) {
            // TempTarget upload
            val list = repository
                .getModifiedTemporaryTargetsDataFromId(lastProcessedId[NightscoutCollection.TEMPORARY_TARGET]!!.get())
                .blockingGet()

            addToLog(EventNSClientNewLog("temptarget SYNC:", "${list.size} records", EventNSClientNewLog.Direction.OUT))
            for (tt in list) {
                aapsLogger.debug(LTag.NSCLIENT, "Uploading $tt")

                // determine id of changed record we are processing
                // for new records it's directly tt.id
                // for modified records gv is in list because of existing new history record
                val lastHistory = repository.getTemporaryTargetsCorrespondingLastHistoryRecord(tt.id)
                val processingId = lastHistory?.id ?: tt.id

                if (lastHistory?.contentEqualsTo(tt) == true) {
                    // expecting only NS identifier change
                    lastProcessedId[NightscoutCollection.TEMPORARY_TARGET]?.store(processingId)
                } else if (lastHistory?.isRecordDeleted(tt) == true) {
                    // expecting only invalidated record
                    nightscoutServiceWrapper.delete(tt)?.blockingGet()?.let { httpResult ->
                        when (httpResult.code) {
                            ResponseCode.RECORD_EXISTS -> {
                                lastProcessedId[NightscoutCollection.TEMPORARY_TARGET]?.store(processingId)
                                addToLog(EventNSClientNewLog("temptarget DELETE:", "${lastHistory.interfaceIDs.nightscoutId}", EventNSClientNewLog.Direction.OUT))
                            }

                            else                       -> {
                                // exit from loop, next try in next round
                                addToLog(EventNSClientNewLog("temptarget DELETE ERROR:", "${httpResult.code}", EventNSClientNewLog.Direction.OUT))
                                return
                            }
                        }
                    } ?: lastProcessedId[NightscoutCollection.TEMPORARY_TARGET]?.store(processingId)
                } else {
                    val httpResult = nightscoutServiceWrapper.updateFromNS(tt).blockingGet()
                    tt.interfaceIDs.nightscoutId = httpResult.location?.id
                    when (httpResult.code) {
                        ResponseCode.RECORD_CREATED   -> {
                            lastProcessedId[NightscoutCollection.TEMPORARY_TARGET]?.store(processingId)
                            compositeDisposable += repository.runTransactionForResult(UpdateTemporaryTargetTransaction(tt)).subscribe()
                            addToLog(EventNSClientNewLog("temptarget UPLOAD NEW:", "${httpResult.location?.id}", EventNSClientNewLog.Direction.OUT))
                        }

                        ResponseCode.RECORD_EXISTS    -> {
                            lastProcessedId[NightscoutCollection.TEMPORARY_TARGET]?.store(processingId)
                            addToLog(EventNSClientNewLog("temptarget UPLOAD EXISTING:", "", EventNSClientNewLog.Direction.OUT))
                        }

                        ResponseCode.DOCUMENT_DELETED -> {
                            lastProcessedId[NightscoutCollection.TEMPORARY_TARGET]?.store(processingId)
                            addToLog(EventNSClientNewLog("temptarget UPLOAD DELETED:", "${httpResult.location?.id}", EventNSClientNewLog.Direction.OUT))
                        }

                        else                          -> {
                            // exit from loop, next try in next round
                            addToLog(EventNSClientNewLog("temptarget UPLOAD ERROR:", "${httpResult.code}", EventNSClientNewLog.Direction.OUT))
                            return
                        }
                    }
                }
            }
        }

        if (ttSync.download) {
            //CGM download
            compositeDisposable.add(
                nightscoutServiceWrapper.getByLastModified(NightscoutCollection.TEMPORARY_TARGET, receiveTimestamp[NightscoutCollection.TEMPORARY_TARGET]!!.get())
                    .doFinally {}
                    .subscribeBy(
                        onSuccess = { response ->
                            val entries = response.body()
                            addToLog(EventNSClientNewLog("temptarget SYNC:", "${entries?.size ?: 0} records", EventNSClientNewLog.Direction.IN))
                            entries?.forEach { it ->
                                // Objectives 0

                                Single.just(it) // Todo: bubble this up with e.g. `flatMap { Observable::fromIterable }`
                                    .flatMap { entryResponseBody ->
                                        repository.findTemporaryTargetByNSIdSingle(entryResponseBody.identifier!!)
                                            .map { Pair(it, entryResponseBody) }
                                    }
                                    .flatMap { (temporaryTargetWrapper: ValueWrapper<TemporaryTarget>, entryResponseBody: EntryResponseBody) ->
                                        handleTemporaryTargetDBStorage(temporaryTargetWrapper, entryResponseBody)
                                    }
                                    .subscribeBy(
                                        onSuccess = { /* yay!!! */ },
                                        onError = { // TODO: put in onErrorReturn to not fail all if one fails.
                                            when (it) {
                                                is BadInputDataException -> {
                                                    // TODO wrong NS data
                                                    addToLog(EventNSClientNewLog("temptarget BAD DATA:", "${it.badData}", EventNSClientNewLog.Direction.IN))
                                                    aapsLogger.error("Bad input data")
                                                }

                                                else                     -> aapsLogger.error("Something bad happened: $it")
                                            }
                                        }
                                    )
                            }

                        },
                        onError = { addToLog(EventNSClientNewLog("temptarget ERROR", "failure: ${it.message}", EventNSClientNewLog.Direction.IN)) }
                    )
            )
        }
    }

    fun uploadGlucoseValue(uploadPair: Pair<GlucoseValue, ValueWrapper<GlucoseValue>>) {
        aapsLogger.debug(LTag.NSCLIENT, "Uploading ${uploadPair.first}") // Todo: move to rxChain. would be called when constructing.

        // determine id of changed record we are processing
        // for new records it's directly gv.id
        // for modified records gv is in list because of existing new history record
        val lastHistory = uploadPair.second
        val gv = uploadPair.first
        val processingId = when (lastHistory) {
                is ValueWrapper.Existing -> lastHistory.value.id
                is ValueWrapper.Absent   -> gv.id
            }

        // we have the following cases:
        // 1) history is same as current one. -> just ignore re. upload?
        //    -> reason: only an interface ID was added.
        //    -> open question: what if there were multiple changes during upload and only the last was in addition of an interfaceID?
        //          Don't we need the first history event after last upload?
        // 2) We have a deletion in last step.
        //    -> send delete to NS
        //    -> open question: Same as 1) first history event after last upload.
        // 3) We do have no history event?
        //    -> upload and store back interfaceID.
        // 4) There has been a change
        //    -> upload
        //
        // 3) and 4) can be combined?
        //
        // Open Question: what happens if we upload to/from multiple NS instances?
        //
        // Open Question: on Error exit loop?
        //  In Rx if we throw a Throwable, the stream gets disposed.
        //  So only do "onErrorReturn" after combining the results?
        //  Atm on download we continue as only one value could be off (e.g. BadInputDataException on eval. mg/dl or mmol/l. ) -> for download good.
        //  One Record at a time? -> concatMap instead of flatMap


        // Further ideas:
        // * contentEqualsTo could be extension functions on GlucoseValue? -> work on null
        // * isRecordDeleted() gives me no hint on expected data (other etc.). `wasDeletedBetween(from: TraceableDBEntry,  to: TraceableDBEntry)`?

        when {
            /*lastHistory?.contentEqualsTo(gv) */false == true -> {
                // expecting only NS identifier change
                lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
            }

            /*lastHistory?.contentEqualsTo(gv) */false == true -> {
                // expecting only invalidated record
                nightscoutServiceWrapper.delete(gv)?.blockingGet()?.let { httpResult ->
                    when (httpResult.code) {
                        ResponseCode.RECORD_EXISTS -> {
                            lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
                            // TODO: addToLog(EventNSClientNewLog("entries DELETE:", "${lastHistory.interfaceIDs.nightscoutId}", EventNSClientNewLog.Direction.OUT))
                        }

                        else                       -> {
                            // exit from loop, next try in next round
                            addToLog(EventNSClientNewLog("entries DELETE ERROR:", "${httpResult.code}", EventNSClientNewLog.Direction.OUT))
                            return
                        }
                    }
                } ?: lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
            }

            else                                     -> {
                val httpResult = nightscoutServiceWrapper.updateFromNS(gv).blockingGet()
                gv.interfaceIDs.nightscoutId = httpResult.location?.id
                when (httpResult.code) {
                    ResponseCode.RECORD_CREATED   -> {
                        lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
                        compositeDisposable += repository.runTransactionForResult(UpdateGlucoseValueTransaction(gv)).subscribe()
                        addToLog(EventNSClientNewLog("entries UPLOAD NEW:", "${httpResult.location?.id}", EventNSClientNewLog.Direction.OUT))
                    }

                    ResponseCode.RECORD_EXISTS    -> {
                        lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
                        addToLog(EventNSClientNewLog("entries UPLOAD EXISTING:", "", EventNSClientNewLog.Direction.OUT))
                    }

                    ResponseCode.DOCUMENT_DELETED -> {
                        lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
                        addToLog(EventNSClientNewLog("entries UPLOAD DELETED:", "${httpResult.location?.id}", EventNSClientNewLog.Direction.OUT))
                    }

                    else                          -> {
                        // exit from loop, next try in next round
                        addToLog(EventNSClientNewLog("entries UPLOAD ERROR:", "${httpResult.code}", EventNSClientNewLog.Direction.OUT))
                        return
                    }
                }
            }
        }
    }

}