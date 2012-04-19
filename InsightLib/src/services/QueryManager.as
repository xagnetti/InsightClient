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

    import mx.collections.ArrayCollection;
    import mx.controls.Alert;
    import mx.rpc.IResponder;
    import mx.rpc.events.FaultEvent;
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
            responders = new Dictionary();

            if ( !allowInstantiation )
            {
                throw new Error( "Error: Instantiation failed: Use QueryManager.instance instead of new, you dummy." );
            }
        }

        [Bindable]
        public var currentStatus : String;
        [Bindable]
        public var dimensions : ArrayCollection;
        private var host : String = "http://localhost:8080/InsightCache/ProxyServlet";
//        private var host : String = "http://adobead-6tm1ol6.eur.adobe.com/Profiles/Custom/API.query";
        private var responders : Dictionary;

        public function createQuery( query : String, alias : String, queryCreatedResponder : IResponder, queryExecutedResponder : IResponder ) : void
        {
            currentStatus = "Loading";

            var urlloader : URLLoader = new URLLoader();
            var urlRequest : URLRequest = new URLRequest();

            urlRequest.url = host + "?Action=create&Format=json&Completion=0&Language=Expression&alias=" + alias;
            urlRequest.method = URLRequestMethod.POST;
            urlRequest.contentType = HTTPService.CONTENT_TYPE_XML;
            urlRequest.data = query;
            urlRequest.requestHeaders[ "alias" ] = alias;
            urlloader.addEventListener( HTTPStatusEvent.HTTP_RESPONSE_STATUS, onQueryCreated );
            urlloader.load( urlRequest );
            dispatchEvent( new Event( "createQuery" ) );

            if ( queryCreatedResponder )
                responders[ alias + "_queryCreated" ] = queryCreatedResponder;

            if ( queryExecutedResponder )
                responders[ alias + "_queryExecuted" ] = queryExecutedResponder;
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
            var alias : String = String( event.currentTarget.url ).split( "&alias=" )[ 1 ];

            if ( responders[ alias + "_queryExecuted" ] )
            {
                IResponder( responders[ alias + "_queryExecuted" ] ).result( event.result.toString() );
            }

            dispatchEvent( new Event( "queryExecuted" ) );
        }

        private function executeCurrentQuery( alias : String, currentQueryId : String ) : void
        {
            if ( currentQueryId != null )
            {
                var s : HTTPService = instanciateService();

                s.method = URLRequestMethod.POST;
                s.contentType = HTTPService.CONTENT_TYPE_XML;
                s.url = host + "?Action=result&Format=json&Completion=0&Query-ID=" + currentQueryId + "&alias=" + alias;
                s.addEventListener( ResultEvent.RESULT, onQueryExecuted );
                s.addEventListener( FaultEvent.FAULT, onQueryExecuteErrored );
                s.send();
                dispatchEvent( new Event( "executeCurrentQuery" ) );
            }
            else
            {
                Alert.show( "queryId is null" );
            }
        }

        private function onQueryExecuteErrored( event : FaultEvent ) : void
        {
            currentStatus = event.message.toString();
            dispatchEvent( new Event( "queryExecuted" ) );
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
            var queryId : String = "";
            var cached : Boolean = false;

            for each ( var headerEntry : URLRequestHeader in event.responseHeaders )
            {
                if ( headerEntry.name == "X-Query-ID" )
                {
                    queryId = headerEntry.value;
                }

                if ( headerEntry.name == "cached" )
                {
                    cached = headerEntry.value.split( ", " )[ headerEntry.value.split( ", " ).length - 1 ] == "true";
                }
            }
            dispatchEvent( new Event( "queryCreated" ) );
            var alias : String = event.responseURL.split( "&alias=" )[ 1 ];

            if ( responders[ alias + "_queryCreated" ] )
            {
                IResponder( responders[ alias + "_queryCreated" ] ).result( { queryId: queryId, cached: cached } );
            }
            executeCurrentQuery( alias, queryId );
        }
    }
}
