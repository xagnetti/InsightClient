package flex.utils.spark
{
    import flash.events.MouseEvent;
    import flash.geom.Rectangle;
    import flash.utils.getDefinitionByName;

    import mx.core.IVisualElement;
    import mx.events.DragEvent;
    import mx.managers.DragManager;

    import spark.components.SkinnableContainer;
    import spark.effects.Move;
    import spark.effects.easing.Power;

    public class SnappableContainer extends SkinnableContainer
    {
        public function SnappableContainer()
        {
            super();

            addEventListener( DragEvent.DRAG_ENTER, fndragEnterHandler );
            addEventListener( DragEvent.DRAG_OVER, fndragOverHandler );
            addEventListener( DragEvent.DRAG_DROP, fndragDropHandler );
        }

        private var _gap : int = 5

        public function get gap() : int
        {
            return _gap;
        }

        public function set gap( value : int ) : void
        {
            _gap = value;
        }

        override public function addElement( element : IVisualElement ) : IVisualElement
        {
            super.addElement( element );
            element.addEventListener( MouseEvent.MOUSE_UP, onElementMouseUp );
            repositionElement( element );
            return element;
        }

        override protected function createChildren() : void
        {
            super.createChildren();

            for ( var i : int = 0; i < numElements; i++ )
            {
                getElementAt( i ).addEventListener( MouseEvent.MOUSE_UP, onElementMouseUp );
                repositionElement( getElementAt( i ) );
            }
        }


        private function onElementMouseUp( event : MouseEvent ) : void
        {
            repositionElement( IVisualElement( event.currentTarget ) );
        }

        private function fndragEnterHandler( event : DragEvent ) : void
        {
            if ( event.dragSource.hasFormat( "itemsByIndex" ) )
                DragManager.acceptDragDrop( SnappableContainer( event.currentTarget ) );
        }

        private function fndragOverHandler( event : DragEvent ) : void
        {
            if ( event.dragSource.hasFormat( "itemsByIndex" ) )
                DragManager.showFeedback( DragManager.COPY );
        }

        private function fndragDropHandler( event : DragEvent ) : void
        {
            event.preventDefault();
            var module : IClassNamable = event.dragSource.dataForFormat( "itemsByIndex" )[ 0 ] as IClassNamable;
            var definition : Class = Class( getDefinitionByName( module.className ) );
            var instance : IVisualElement = IVisualElement( new definition() );

            instance.x = event.localX;
            instance.y = event.localY;
            addElement( instance );
        }

        private function repositionElement( droppedComponent : IVisualElement ) : void
        {
            var yTarget : Number = droppedComponent.y;

            if ( !childrenCollide( droppedComponent, yTarget ) )
            {
                while ( !childrenCollide( droppedComponent, yTarget ) && yTarget > 0 )
                {
                    yTarget--;
                }
                yTarget += gap;
            }
            else
            {
                while ( childrenCollide( droppedComponent, yTarget ) && droppedComponent.y < height )
                {
                    yTarget++;
                }
                yTarget += gap;
            }
            var move : Move = new Move( droppedComponent );

            move.easer = new Power();
            move.yTo = yTarget;
            move.play();
        }

        private function childrenCollide( reference : IVisualElement, currentY : Number ) : Boolean
        {
            for ( var i : int = 0; i < numElements; i++ )
            {
                var child : IVisualElement = getElementAt( i );

                if ( twoComponentsCollide( child, reference, currentY ) && child != reference )
                    return true;
            }
            return false;
        }

        private static function twoComponentsCollide( obj1 : IVisualElement, obj2 : IVisualElement, currentY : Number ) : Boolean
        {
            var bounds1 : Rectangle = new Rectangle( obj1.x, obj1.y, obj1.width, obj1.height );
            var bounds2 : Rectangle = new Rectangle( obj2.x, currentY, obj2.width, obj2.height );

            return bounds1.intersects( bounds2 );
        }
    }
}
