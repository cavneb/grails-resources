package org.grails.plugin.resource

import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Holder for info about a resource declaration at runtime
 */
class ResourceModule {
    String name
    String cachedMarkup // Saves calling the tags every time
    
    List<ResourceMeta> resources = []
    List<String> dependsOn = []
    def defaultBundle
    
    def pluginManager
    
    ResourceModule(name, ResourceService svc) {
        this.name = name
        this.pluginManager = svc.pluginManager
    }
    
    ResourceModule(name, Map resourceInfo, ResourceService svc) {
        this(name, svc)
        def args = [:]
        args.putAll(resourceInfo)
        if (args.url instanceof Map) {
            args.url = getResourceUrl(args.url)
        }
        this.resources << newResourceFromArgs(args, svc)
        lockDown()
    }

    ResourceModule(name, List resourceInfoList, ResourceService svc) {
        this(name, svc)
        resourceInfoList.each { i ->
            if (i instanceof Map) {
                def args = i.clone()
                if (args.url instanceof Map) {
                    args.url = getResourceUrl(args.url)
                }
                def r = newResourceFromArgs(args, svc)
                this.resources << r
            } else if (i instanceof String) {
                this.resources << newResourceFromArgs(url:i, svc)
            } else {
                throw new IllegalArgumentException("Barf!")
            }
        }
        lockDown()
    }
    
    void addModuleDependency(String name) {
        dependsOn << name
    }
    
    def getBundleTypes() {
        ['css', 'js']
    }
    
    ResourceMeta newResourceFromArgs(Map args, ResourceService svc) {
        def url = args.remove('url')
        if (url) {
            if (!url.contains('://') && !url.startsWith('/')) {
                url = '/'+url
            }
        }
        def r = new ResourceMeta(sourceUrl: url , workDir: svc.workDir, module:this)
        def ti = svc.getDefaultSettingsForURI(url, args.attrs?.type)
        if (!ti) {
            throw new IllegalArgumentException("Cannot create resource $url, is not a supported type")
        }
        r.disposition = args.remove('disposition') ?: ti.disposition
        r.linkOverride = args.remove('linkOverride')
        r.bundle = args.remove('bundle')
        if (!r.bundle && (r.sourceUrlExtension in bundleTypes)) {
            if (defaultBundle == null) {
                // use module name by default
                r.bundle = name
            } else if (defaultBundle) { 
                // use supplied value as a default
                r.bundle = defaultBundle.toString()
            }
        }
        r.prePostWrapper = args.remove('wrapper')
        def resattrs = ti.attrs?.clone() ?: [:]
        def attrs = args.remove('attrs')
        if (attrs) {
            resattrs += attrs
        }
        r.tagAttributes = attrs?.clone()
        r.attributes.putAll(args)
        return r        
    }
    
    void lockDown() {
        this.resources = this.resources.asImmutable()
    }
    
    
// ********************* EVIL - I HATE INABILITY TO REUSE! ***********************
    /**
     * Copied from ApplicationTagLib
     */
    String makeServerURL() {
        def u = ConfigurationHolder.config?.grails?.serverURL
        if (!u) {
            // Leave it null if we're in production so we can throw
            if (Environment.current != Environment.PRODUCTION) {
                u = "http://localhost:" +(System.getProperty('server.port') ? System.getProperty('server.port') : "8080")
            }
        }
        return u
    }

    /**
     * Resolve the normal link/resource attributes map (plugin, dir, file) to a link
     * relative to the host (not app context)
     * This is basically g.resource copied and pasted
     */
    def getResourceUrl(Map args) {
        def s = new StringBuilder() // Java 5? bite me
    
        // Ugly copy and paste from ApplicationTagLib
        def base = args.remove('base')
        if (base) {
            s << base
        } else {
            def abs = args.remove("absolute")
            if (Boolean.valueOf(abs)) {
                def u = makeServerURL()
                if (u) {
                    s << u
                } else {
                    throw new IllegalArgumentException("Attribute absolute='true' specified but no grails.serverURL set in Config")
                }
            }
            else {
                // @todo work out how to get servlet context path
                // For servlets SDK 2.5 you can servletContext.getContextPath()
                s << ''
            }
        }

        def dir = args['dir']
        if (args.plugin) {
            s << pluginManager.getPluginPath(args.plugin) ?: ''
        }
        if (dir) {
            s << (dir.startsWith("/") ?  dir : "/${dir}")
        }
        def file = args['file']
        if (file) {
            s << (file.startsWith("/") || dir?.endsWith('/') ?  file : "/${file}")
        }    
        return s.toString()
    }
    
}