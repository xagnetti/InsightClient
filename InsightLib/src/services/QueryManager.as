package services
{
    import flash.events.Event;
    import flash.events.EventDispatcher;
    import flash.events.HTTPStatusEvent;
    import flash.net.URLLoader;
    import flash.net.URLRequest;
    import flash.net.URLRequestHeader;
    import flash.net.URLRequestMethod;
    import flash.utils.Dictionary;

    import model.QueryModel;

    import mx.collections.ArrayCollection;
    import mx.controls.Alert;
    import mx.rpc.events.ResultEvent;
    import mx.rpc.http.HTTPService;

    [Event( name = "queryCreated", type = "flash.events.Event" )]
    [Event( name = "schemaRetrieved", type = "flash.events.Event" )]
    [Event( name = "executeCurrentQuery", type = "flash.events.Event" )]
    [Event( name = "queryExecuted", type = "flash.events.Event" )]
    [Event( name = "createQuery", type = "flash.events.Event" )]
    [Event( name = "getSchema", type = "flash.events.Event" )]
    [Event( name = "queryCompleted", type = "flash.events.Event" )]
    public final class QueryManager extends EventDispatcher
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
        public var currentResult : String;
        [Bindable]
        public var dimensions : ArrayCollection;
        private var currentQuery : QueryModel = null;
//		private var host : String = "http://localhost:8080/InsightCache/ProxyServlet";
        private var host : String = "http://adobead-6tm1ol6.eur.adobe.com/InsightCache/ProxyServlet";
        private var queries : Dictionary;

        public function createQuery( query : String, alias : String ) : void
        {
            currentResult = "Loading";

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
                dispatchEvent( new Event( "createQuery" ) );
            }
        }

        public function getSchema() : void
        {
            var s : HTTPService = instanciateService();

            s.addEventListener( ResultEvent.RESULT, onGetSchemaResultHandler );
            s.send( { Action: "get-schema", Format: "json-flat" } );
            dispatchEvent( new Event( "getSchema" ) );
        }

        protected function onQueryExecuted( event : ResultEvent ) : void
        {
            currentResult = event.result.toString();
            currentQuery = null;
            dispatchEvent( new Event( "queryExecuted" ) );
        }

        private function executeCurrentQuery() : void
        {
            if ( currentQuery != null && currentQuery.id != null )
            {
                var s : HTTPService = instanciateService();

                s.method = URLRequestMethod.POST;
                s.contentType = HTTPService.CONTENT_TYPE_XML;
                s.url = host + "?Action=result&Format=Json&Completion=0&Query-ID=" + currentQuery.id;
                s.addEventListener( ResultEvent.RESULT, onQueryExecuted );
                s.send();
                dispatchEvent( new Event( "executeCurrentQuery" ) );
            }
            else
            {
                Alert.show( "queryId is null" );
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
            dispatchEvent( new Event( "schemaRetrieved" ) );
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
            dispatchEvent( new Event( "queryCreated" ) );
            executeCurrentQuery();
        }
    }
}
