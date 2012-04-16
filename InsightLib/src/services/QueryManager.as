package services
{
    import flash.events.Event;
    import flash.events.HTTPStatusEvent;
    import flash.net.URLLoader;
    import flash.net.URLRequest;
    import flash.net.URLRequestHeader;
    import flash.net.URLRequestMethod;
    import flash.utils.Dictionary;

    import model.QueryModel;

    import mx.collections.ArrayCollection;
    import mx.rpc.AsyncToken;
    import mx.rpc.events.ResultEvent;
    import mx.rpc.http.HTTPService;

    public final class QueryManager
    {
        private static var _instance : QueryManager;
        private static var allowInstantiation : Boolean;

        public static function get instance() : QueryManager
        {
            if ( _instance == null )
            {
                allowInstantiation = true;
                _instance = new QueryManager();
                allowInstantiation = false;
            }
            return _instance;
        }

        public function QueryManager() : void
        {
            dimensions = new ArrayCollection();
            queries = new Dictionary();

            if ( !allowInstantiation )
            {
                throw new Error( "Error: Instantiation failed: Use QueryManager.instance instead of new." );
            }
        }

        [Bindable]
        public var dimensions : ArrayCollection;
        private var currentQuery : QueryModel = null;
        private var host : String = "http://adobead-6tm1ol6.eur.adobe.com/Profiles/Custom/API.query";
        private var queries : Dictionary;

        public function createQuery( query : String, alias : String ) : void
        {
            if ( currentQuery == null )
            {
                var urlloader : URLLoader = new URLLoader();
                var urlRequest : URLRequest = new URLRequest();

                currentQuery = new QueryModel();
                currentQuery.query = query;
                currentQuery.alias = alias;
                urlRequest.url = host + "?Action=create&Format=Json";
                urlRequest.method = URLRequestMethod.POST;
                urlRequest.contentType = HTTPService.CONTENT_TYPE_XML;
                urlRequest.data = query;
                urlloader.addEventListener( HTTPStatusEvent.HTTP_RESPONSE_STATUS, onQueryCreated );
                urlloader.load( urlRequest );
            }
        }

        public function getSchema() : void
        {
            var s : HTTPService = instanciateService();

            s.addEventListener( ResultEvent.RESULT, onGetSchemaResultHandler );
            s.send( { Action: "get-schema", Format: "json-flat" } );
        }

        protected function onQueryCompleted( event : Event ) : void
        {
            currentQuery = null;
        }

        protected function onQueryResponded( event : HTTPStatusEvent ) : void
        {
            for each ( var headerEntry : URLRequestHeader in event.responseHeaders )
            {
                if ( headerEntry.name == "X-Error" )
                {
                    currentQuery.error = headerEntry.value;
                }
            }
        }

        private function executeCurrentQuery() : void
        {
            if ( currentQuery != null )
            {
                var s : HTTPService = instanciateService();

                s.method = URLRequestMethod.POST;
                s.contentType = HTTPService.CONTENT_TYPE_XML;
                s.url = host + "?Action=result&Format=Json&Completion=0&Query-ID=" + currentQuery.id;
                s.addEventListener( ResultEvent.RESULT, onQueryCompleted );
                s.send( { Action: "get-schema", Format: "json-flat" } );
            }
        }

        private function instanciateService() : HTTPService
        {
            var s : HTTPService = new HTTPService();

            s.url = host;
            s.resultFormat = HTTPService.RESULT_FORMAT_OBJECT;
            s.headers = { Accept: "application/json" };

            return s;
        }

        private function onGetSchemaResultHandler( event : ResultEvent ) : void
        {
            var obj : Object = JSON.parse( event.result.toString() );

            for each ( var i : Object in obj )
            {
                if ( i[ "type" ] == "SchemaDim" )
                {
                    dimensions.addItem( i[ "name" ] )
                }
            }
        }

        private function onQueryCreated( event : HTTPStatusEvent ) : void
        {
            for each ( var headerEntry : URLRequestHeader in event.responseHeaders )
            {
                if ( headerEntry.name == "X-Query-ID" )
                {
                    currentQuery.id = headerEntry.value;
                    queries[ currentQuery.alias ] = currentQuery;
                    break;
                }
            }
            executeCurrentQuery();
        }
    }
}
