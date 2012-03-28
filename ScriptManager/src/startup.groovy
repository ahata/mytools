import org.codehaus.groovy.control.CompilerConfiguration

def config = new CompilerConfiguration()
config.setSourceEncoding("utf-8")

def shell = new GroovyShell(this.getBinding(), config).parse(new ByteArrayInputStream(_src_.getBytes("utf-8")))
shell.metaClass.methodMissing = {String name, args->
	def binding = new Binding(this.binding.variables)
	binding.setVariable("args", args)
	_engine_.run(name, binding)
}
use(_categories_) {
	shell.run()
}
