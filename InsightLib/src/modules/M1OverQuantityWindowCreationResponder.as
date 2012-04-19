package modules
{
    import mx.rpc.IResponder;
    import mx.rpc.Responder;

    public class M1OverQuantityWindowCreationResponder implements IResponder
    {
        [Bindable]
        public var cached : Boolean;

        public function result( data : Object ) : void
        {
            cached = data.cached;
        }

        public function fault( info : Object ) : void
        {
        }
    }
}
