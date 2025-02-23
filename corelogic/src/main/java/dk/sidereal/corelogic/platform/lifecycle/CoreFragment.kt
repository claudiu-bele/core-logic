package dk.sidereal.corelogic.platform.lifecycle

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModel
import dk.sidereal.corelogic.kotlin.ext.simpleTagName
import dk.sidereal.corelogic.platform.ControllerHolder
import dk.sidereal.corelogic.platform.HandlesBackPress
import dk.sidereal.corelogic.platform.ext.hasPermission
import dk.sidereal.corelogic.platform.vm.ViewModelAc

/** Base fragment to be used with [CoreActivity], although it is compatible with non-[CoreActivity] subclasses aswell.
 *
 */
open class CoreFragment : DialogFragment(), ControllerHolder<FragmentController>, HandlesBackPress {

    companion object {
        val INNER_TAG by lazy { CoreFragment::class.simpleTagName() }
    }

    val application: Application?
        get() = activity?.application
    val requireApplication: Application
        get() = requireCoreActivity.application
    val coreApplication: CoreApplication?
        get() = coreActivity?.coreApplication
    /** Will throw if application is not of type [CoreApplication]
     *
     */
    val requireCoreApplication: CoreApplication
        get() = requireCoreActivity.application as CoreApplication
    // activity and requireActivity already exist
    val coreActivity: CoreActivity?
        get() = activity as? CoreActivity
    val requireCoreActivity: CoreActivity
        get() = requireActivity() as CoreActivity

    val coreFragments: List<CoreFragment>
        get() = childFragmentManager.fragments.dropWhile { it !is CoreFragment }.map { it as CoreFragment }

    protected val TAG by lazy { javaClass.simpleTagName() }

    override var mutableControllers: MutableList<FragmentController> = mutableListOf()

    //region Lifecycle
    override fun onAttach(context: Context) {
        super.onAttach(context)
        onCreateControllers()
        Log.d(TAG, "onAttach")
        mutableControllers.forEach { it.onAttach(context) }
    }

    data class PermissionRequest(val requestCode: Int,
                                 val permissions: Array<out String>,
                                 val onGrantResults: (List<PermissionResponse>)->Unit)

    data class PermissionResponse(val permission: String,
                                  val granted: Boolean,
                                  val dontShowAgain: Boolean = false)

    val permissions = mutableListOf<PermissionRequest>()

    fun requestPermissions(permissionRequest: PermissionRequest){

        val grants = permissionRequest.permissions.map {
            it to requireContext().hasPermission(it)
        }
        val notGranted = grants.filter { !it.second }.map { it.first }

        // if 0 not granted means all are granted, call callback
        if(notGranted.isEmpty()) {
            permissionRequest.onGrantResults(permissionRequest.permissions.map { PermissionResponse(it, true, true) })
            return
        }
        permissions.add(permissionRequest)
//        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
//            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
//        } else {
        requestPermissions(permissionRequest.permissions, permissionRequest.requestCode)
//        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        this.permissions.firstOrNull { it.requestCode == requestCode }?.let { request ->
            val permissionResponses = permissions.mapIndexed { index, permission ->
                var dontShowAgain = false
                if(grantResults[index] == PackageManager.PERMISSION_DENIED) {
                   dontShowAgain =  shouldShowRequestPermissionRationale(permission).not()
                }
                PermissionResponse(permission,grantResults[index] == PackageManager.PERMISSION_GRANTED, dontShowAgain)
            }
            request.onGrantResults(permissionResponses)
            this.permissions.remove(request)
        }
    }

    /** Called in [CoreFragment.onAttach]. Add your outControllers to the passed parameter
     */
    override fun onCreateControllers(outControllers: MutableList<FragmentController>) {
        super.onCreateControllers(outControllers)
    }
    // endregion

    /** Called in [CoreActivity.onNavigateUp]
     *
     */
    protected open fun onNavigateUp(): Boolean {
        var handledNavigateUp = false
        mutableControllers.forEach {
            if (!handledNavigateUp) {
                handledNavigateUp = handledNavigateUp or it.onNavigateUp()
            }
        }
        return handledNavigateUp
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mutableControllers.forEach { it.onActivityResult(requestCode, resultCode, data) }
    }


    /** Called by [CoreActivity.onBackPressed]
     * Return true to flag that the fragment
     * handled the back internally and that the
     * activity shouldn't call super
     *
     */
    override fun onBackPressedInternal(): Boolean {
        Log.d("alt-nav", "CoreFragment (${this::class.java.simpleName}) onBackPressedInternal")
        var handledBackPressed = false
        mutableControllers.forEach {
            if (!handledBackPressed) {
                handledBackPressed = handledBackPressed or it.onBackPressed()
            }
        }
        Log.d(
            "alt-nav",
            "CoreFragment (${this::class.java.simpleName}) controllers handled back: $handledBackPressed"
        )
        if (handledBackPressed) {
            return true
        }
        childFragmentManager.fragments.reversed().forEach {
            if (!handledBackPressed) {
                handledBackPressed = ((it as? HandlesBackPress)?.onBackPressedInternal() ?: false)
            }
        }
        Log.d(
            "alt-nav",
            "CoreFragment (${this::class.java.simpleName}) child fragments handled back: $handledBackPressed"
        )

        if (handledBackPressed) {
            return true
        }
        handledBackPressed = onBackPressed()
        Log.d(
            "alt-nav",
            "CoreFragment (${this::class.java.simpleName}) onBackPressed handled back: $handledBackPressed"
        )
        return handledBackPressed
    }

    /** Called from [CoreFragment.onBackPressedInternal]
     * if no attached [ActivityController] returns true in [ActivityController.onBackPressed]
     *
     */
    override fun onBackPressed(): Boolean = false

    /** Called in [CoreActivity.onDestroy]
     *
     */
    open fun onActivityDestroyed() {}


    /** Retrieves the desired view model. Will create it if neeeded. For supported viewmodel
     * classes and constructors for them, check [ViewModelAc]
     *
     * Will throw exception if fragment detached and [getActivity] null
     *
     */
    fun <T : ViewModel> getVm(clazz: Class<T>): T {
        checkNotNull(coreActivity)
        val vmController = coreActivity!!.getController(ViewModelAc::class.java)
        checkNotNull(vmController)
        return vmController.get(clazz)
    }


}