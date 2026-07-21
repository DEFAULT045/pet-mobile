package com.llz121517.meapet.live2d

import com.live2d.sdk.cubism.framework.CubismDefaultParameterId
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.CubismModelSettingJson
import com.live2d.sdk.cubism.framework.ICubismModelSetting
import com.live2d.sdk.cubism.framework.effect.CubismLook
import com.live2d.sdk.cubism.framework.id.CubismId
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.model.CubismMoc
import com.live2d.sdk.cubism.framework.model.CubismUserModel
import com.live2d.sdk.cubism.framework.motion.ACubismMotion
import com.live2d.sdk.cubism.framework.motion.CubismExpressionMotion
import com.live2d.sdk.cubism.framework.motion.CubismExpressionUpdater
import com.live2d.sdk.cubism.framework.motion.CubismLookUpdater
import com.live2d.sdk.cubism.framework.motion.CubismMotion
import com.live2d.sdk.cubism.framework.motion.CubismPhysicsUpdater
import com.live2d.sdk.cubism.framework.motion.CubismPoseUpdater
import com.live2d.sdk.cubism.framework.rendering.CubismRenderer
import com.live2d.sdk.cubism.framework.rendering.android.CubismRenderTargetAndroid
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.live2d.sdk.cubism.framework.utils.CubismDebug

/**
 * Core Live2D model wrapper extending CubismUserModel.
 * Loads .moc3, textures, motions, physics, pose, and expressions.
 */
class Live2dModel(modelDirName: String) : CubismUserModel() {

    val renderingBuffer = CubismRenderTargetAndroid()
    var motionUpdated = false
        private set

    private val idManager = CubismFramework.getIdManager()
    private val idParamAngleX = idManager.getId(CubismDefaultParameterId.ParameterId.ANGLE_X.id)
    private val idParamAngleY = idManager.getId(CubismDefaultParameterId.ParameterId.ANGLE_Y.id)
    private val idParamAngleZ = idManager.getId(CubismDefaultParameterId.ParameterId.ANGLE_Z.id)
    private val idParamBodyAngleX = idManager.getId(CubismDefaultParameterId.ParameterId.BODY_ANGLE_X.id)
    private val idParamEyeBallX = idManager.getId(CubismDefaultParameterId.ParameterId.EYE_BALL_X.id)
    private val idParamEyeBallY = idManager.getId(CubismDefaultParameterId.ParameterId.EYE_BALL_Y.id)

    private var modelHomeDirectory = modelDirName
    private var modelSetting: ICubismModelSetting? = null

    private val motions = mutableMapOf<String, ACubismMotion>()
    private val expressions = mutableMapOf<String, ACubismMotion>()
    private val eyeBlinkIds = mutableListOf<CubismId>()
    private val lipSyncIds = mutableListOf<CubismId>()

    init {
        mocConsistency = Live2dDefine.MOC_CONSISTENCY_VALIDATION_ENABLE
    }

    /** User-time seconds accumulator */
    private var userTimeSeconds = 0.0f

    fun loadAssets(dir: String, fileName: String) {
        modelHomeDirectory = dir
        setupModel("$dir$fileName")

        val renderer = CubismRendererAndroid.create(
            Live2dDelegate.getInstance().windowWidth,
            Live2dDelegate.getInstance().windowHeight
        )
        setupRenderer(renderer)

        setupTextures()
    }

    fun deleteModel() {
        delete()
    }

    fun reloadRenderer() {
        deleteRenderer()

        val renderer = CubismRendererAndroid.create(
            Live2dDelegate.getInstance().windowWidth,
            Live2dDelegate.getInstance().windowHeight
        )
        setupRenderer(renderer)
        setupTextures()
    }

    fun update() {
        isUpdated(false)

        val deltaTimeSeconds = Live2dPal.getDeltaTime()
        userTimeSeconds += deltaTimeSeconds

        motionUpdated = false
        model!!.loadParameters()

        // Auto-start idle motion if nothing is playing
        if (motionManager.isFinished()) {
            startMotion(Live2dDefine.MotionGroup.IDLE, 0, Live2dDefine.Priority.IDLE)
        } else {
            motionUpdated = motionManager.updateMotion(model!!, deltaTimeSeconds)
        }

        model!!.saveParameters()
        updateScheduler.onLateUpdate(model!!, deltaTimeSeconds)
        model!!.update()

        isUpdated(true)
    }

    fun startMotion(group: String, number: Int, priority: Int): Int {
        if (priority == Live2dDefine.Priority.FORCE) {
            motionManager.setReservationPriority(priority)
        } else if (!motionManager.reserveMotion(priority)) {
            if (Live2dDefine.DEBUG_LOG_ENABLE) {
                CubismFramework.coreLogFunction("[APP] cannot start motion.")
            }
            return -1
        }

        val modelSetting = modelSetting ?: return -1
        val fileName = modelSetting.getMotionFileName(group, number)
        val name = "${group}_$number"

        var motion = motions[name] as? CubismMotion

        if (motion == null && fileName.isNotEmpty()) {
            val path = modelHomeDirectory + fileName
            val buffer = Live2dPal.loadFileAsBytes(path)
            val tmp = loadMotion(buffer) as? CubismMotion
            if (tmp != null) {
                motion = tmp
                val fadeIn = modelSetting.getMotionFadeInTimeValue(group, number)
                if (fadeIn != -1.0f) motion.fadeInTime = fadeIn
                val fadeOut = modelSetting.getMotionFadeOutTimeValue(group, number)
                if (fadeOut != -1.0f) motion.fadeOutTime = fadeOut
            }
        }

        if (Live2dDefine.DEBUG_LOG_ENABLE) {
            CubismFramework.coreLogFunction("[APP] start motion: ${group}_$number")
        }

        return motionManager.startMotionPriority(motion, priority)
    }

    fun draw(matrix: CubismMatrix44) {
        if (model == null) {
            try {
                Live2dDelegate.getInstance().activity.finish()
            } catch (_: Exception) { /* not always in an activity */ }
            return
        }

        // Apply model matrix to the projection matrix
        CubismMatrix44.multiply(
            modelMatrix!!.array,
            matrix.array,
            matrix.array
        )

        castRenderer<CubismRendererAndroid>().apply {
            setMvpMatrix(matrix)
            drawModel()
        }
    }

    fun hasMocConsistencyFromFile(mocFileName: String): Boolean {
        val path = modelHomeDirectory + mocFileName
        val buffer = Live2dPal.loadFileAsBytes(path)
        val consistent = CubismMoc.hasMocConsistency(buffer)
        CubismDebug.cubismLogInfo(if (consistent) "Consistent MOC3." else "Inconsistent MOC3.")
        return consistent
    }

    /**
     * Returns the model setting (model3.json data). Used by overlay service.
     */
    fun getModelSetting(): ICubismModelSetting? = modelSetting

    /**
     * Get the model home directory.
     */
    fun getModelHomeDirectory(): String = modelHomeDirectory

    /**
     * (Re-)bind textures using a custom texture manager.
     * Used by the overlay service which has its own GL context and texture manager.
     */
    fun bindTextures(tm: Live2dTextureManager) {
        val ms = modelSetting ?: return
        for (i in 0 until ms.textureCount) {
            val texName = ms.getTextureFileName(i)
            if (texName.isEmpty()) continue
            val texPath = modelHomeDirectory + texName
            val texInfo = tm.createTextureFromPngFile(texPath)
            castRenderer<CubismRendererAndroid>().bindTexture(i, texInfo.id)
            castRenderer<CubismRendererAndroid>().isPremultipliedAlpha(Live2dDefine.PREMULTIPLIED_ALPHA_ENABLE)
        }
    }

    // ---- private helpers ----

    @Suppress("UNCHECKED_CAST")
    private fun <T : CubismRenderer> castRenderer(): T = getRenderer() as T

    private fun setupModel(model3JsonPath: String): Boolean {
        val model3Json = Live2dPal.loadFileAsBytes(model3JsonPath)
        modelSetting = CubismModelSettingJson(model3Json)

        if (modelSetting?.json == null) {
            if (Live2dDefine.DEBUG_LOG_ENABLE) {
                CubismFramework.coreLogFunction("[ERROR] model3.json is not found")
            }
            try {
                Live2dDelegate.getInstance().activity.finish()
            } catch (_: Exception) { /* overlay mode — no activity to finish */ }
            return false
        }

        // Load .moc3
        val mocPath = modelHomeDirectory + modelSetting!!.modelFileName
        if (mocPath.isNotEmpty()) {
            val buffer = Live2dPal.loadFileAsBytes(mocPath)
            loadModel(buffer, mocConsistency)
        }

        // Expressions
        val expCount = modelSetting!!.expressionCount
        if (expCount > 0) {
            for (i in 0 until expCount) {
                val name = modelSetting!!.getExpressionName(i)
                val path = modelHomeDirectory + modelSetting!!.getExpressionFileName(i)
                val buffer = Live2dPal.loadFileAsBytes(path)
                loadExpression(buffer)?.let { expressions[name] = it }
            }
            updateScheduler.addUpdatableList(CubismExpressionUpdater(expressionManager))
        }

        // Pose
        val posePath = modelSetting!!.poseFileName
        if (posePath.isNotEmpty()) {
            val buffer = Live2dPal.loadFileAsBytes(modelHomeDirectory + posePath)
            loadPose(buffer)
        }
        if (pose != null) {
            updateScheduler.addUpdatableList(CubismPoseUpdater(pose))
        }

        // Physics
        val physicsPath = modelSetting!!.physicsFileName
        if (physicsPath.isNotEmpty()) {
            val buffer = Live2dPal.loadFileAsBytes(modelHomeDirectory + physicsPath)
            loadPhysics(buffer)
        }
        if (physics != null) {
            updateScheduler.addUpdatableList(CubismPhysicsUpdater(physics))
        }

        // UserData
        val userDataPath = modelSetting!!.userDataFile
        if (userDataPath.isNotEmpty()) {
            val buffer = Live2dPal.loadFileAsBytes(modelHomeDirectory + userDataPath)
            loadUserData(buffer)
        }

        // Look (eye tracking by drag)
        val look = CubismLook.create()
        look.setParameters(
            listOf(
                CubismLook.LookParameterData(idParamAngleX, 30.0f),
                CubismLook.LookParameterData(idParamAngleY, 0.0f, 30.0f),
                CubismLook.LookParameterData(idParamAngleZ, 0.0f, 0.0f, -30.0f),
                CubismLook.LookParameterData(idParamBodyAngleX, 10.0f),
                CubismLook.LookParameterData(idParamEyeBallX, 1.0f),
                CubismLook.LookParameterData(idParamEyeBallY, 0.0f, 1.0f)
            )
        )
        updateScheduler.addUpdatableList(CubismLookUpdater(look, dragManager))

        updateScheduler.sortUpdatableList()

        // Layout
        val layout = mutableMapOf<String, Float>()
        modelSetting!!.getLayoutMap(layout)
        modelMatrix!!.setupFromLayout(layout)

        model!!.saveParameters()

        // Preload motions
        for (i in 0 until modelSetting!!.motionGroupCount) {
            val group = modelSetting!!.getMotionGroupName(i)
            preLoadMotionGroup(group)
        }

        motionManager.stopAllMotions()
        return true
    }

    private fun preLoadMotionGroup(group: String) {
        val count = modelSetting!!.getMotionCount(group)
        for (i in 0 until count) {
            val name = "${group}_$i"
            val path = modelSetting!!.getMotionFileName(group, i)
            if (path.isEmpty()) continue

            val fullPath = modelHomeDirectory + path
            val buffer = Live2dPal.loadFileAsBytes(fullPath)
            val tmp = loadMotion(buffer) as? CubismMotion ?: continue

            val fadeIn = modelSetting!!.getMotionFadeInTimeValue(group, i)
            if (fadeIn != -1.0f) tmp.fadeInTime = fadeIn
            val fadeOut = modelSetting!!.getMotionFadeOutTimeValue(group, i)
            if (fadeOut != -1.0f) tmp.fadeOutTime = fadeOut
            tmp.setEffectIds(eyeBlinkIds, lipSyncIds)

            motions[name] = tmp
        }
    }

    private fun setupTextures() {
        val modelSetting = modelSetting ?: return
        for (i in 0 until modelSetting.textureCount) {
            val texName = modelSetting.getTextureFileName(i)
            if (texName.isEmpty()) continue

            val texPath = modelHomeDirectory + texName
            val texInfo = Live2dDelegate.getInstance()
                .textureManager
                .createTextureFromPngFile(texPath)

            castRenderer<CubismRendererAndroid>().bindTexture(i, texInfo.id)
            castRenderer<CubismRendererAndroid>().isPremultipliedAlpha(Live2dDefine.PREMULTIPLIED_ALPHA_ENABLE)
        }
    }
}
