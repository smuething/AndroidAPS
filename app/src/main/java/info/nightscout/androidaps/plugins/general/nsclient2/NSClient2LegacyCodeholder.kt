package info.nightscout.androidaps.plugins.general.nsclient2

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.UpdateGlucoseValueTransaction
import info.nightscout.androidaps.database.transactions.UpdateTemporaryTargetTransaction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.networking.nightscout.NightscoutService
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
    nightscoutService: NightscoutService,
    fabricPrivacy: FabricPrivacy,
    aapsSchedulers: AapsSchedulers,
    repository: AppRepository,
    nsClientSourcePlugin: NSClientSourcePlugin
) : NSClient2Plugin(aapsLogger, resourceHelper, injector, context, rxBus, sp, receiverStatusStore, nightscoutService, fabricPrivacy, aapsSchedulers, repository, nsClientSourcePlugin
) {

    fun legacyDoSync() {

        val cgmSync = PreferenceString(R.string.key_ns_cgm, "PUSH", sp, resourceHelper, rxBus)
        val ttSync = PreferenceString(R.string.key_ns_temptargets, "PUSH", sp, resourceHelper, rxBus)

        if (cgmSync.upload) {
            // CGM upload
            val list = repository
                .getModifiedBgReadingsDataFromId(lastProcessedId[NightscoutCollection.ENTRIES]!!.get())
                .blockingGet()

            addToLog(EventNSClientNewLog("entries SYNC:", "${list.size} records", EventNSClientNewLog.Direction.OUT))
            for (gv in list) {
                aapsLogger.debug(LTag.NSCLIENT, "Uploading $gv")

                // determine id of changed record we are processing
                // for new records it's directly gv.id
                // for modified records gv is in list because of existing new history record
                val lastHistory = repository.getBgReadingsCorrespondingLastHistoryRecord(gv.id)
                val processingId = lastHistory?.id ?: gv.id

                if (lastHistory?.contentEqualsTo(gv) == true) {
                    // expecting only NS identifier change
                    lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
                } else if (lastHistory?.isRecordDeleted(gv) == true) {
                    // expecting only invalidated record
                    nightscoutService.delete(gv)?.blockingGet()?.let { httpResult ->
                        when (httpResult.code) {
                            ResponseCode.RECORD_EXISTS -> {
                                lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
                                addToLog(EventNSClientNewLog("entries DELETE:", "${lastHistory.interfaceIDs.nightscoutId}", EventNSClientNewLog.Direction.OUT))
                            }

                            else                       -> {
                                // exit from loop, next try in next round
                                addToLog(EventNSClientNewLog("entries DELETE ERROR:", "${httpResult.code}", EventNSClientNewLog.Direction.OUT))
                                return
                            }
                        }
                    } ?: lastProcessedId[NightscoutCollection.ENTRIES]?.store(processingId)
                } else {
                    val httpResult = nightscoutService.updateFromNS(gv).blockingGet()
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
                    nightscoutService.delete(tt)?.blockingGet()?.let { httpResult ->
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
                    val httpResult = nightscoutService.updateFromNS(tt).blockingGet()
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
                nightscoutService.getByLastModified(NightscoutCollection.TEMPORARY_TARGET, receiveTimestamp[NightscoutCollection.TEMPORARY_TARGET]!!.get())
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

}